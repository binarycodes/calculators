package io.binarycodes.calculators.debt.service;

import io.binarycodes.calculators.base.math.Rates;
import io.binarycodes.calculators.debt.domain.Debt;
import io.binarycodes.calculators.debt.domain.DebtPayment;
import io.binarycodes.calculators.debt.domain.DebtPlanInputs;
import io.binarycodes.calculators.debt.domain.DebtPlanResult;
import io.binarycodes.calculators.debt.domain.DebtPlanYear;
import io.binarycodes.calculators.debt.domain.DebtScheduleResult;
import io.binarycodes.calculators.debt.domain.MonthlyPayment;
import io.binarycodes.calculators.debt.domain.PayoffStrategy;
import io.binarycodes.calculators.debt.domain.Windfall;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Month-by-month multi-debt payoff simulator.
 *
 * <p>The user commits a single {@link DebtPlanInputs#getMonthlyBudget() monthly
 * budget} — the most they can pay. Each month that budget (grown by the annual
 * step-up, plus any windfall) is distributed across the debts: it first covers
 * each debt's effective minimum in the strategy's fixed order, then funnels the
 * remainder to the strategy's target, cascading within the month as debts clear.
 * If the budget cannot cover every minimum, the debts it can't reach that month
 * are marked as <b>defaulting</b> — no money is invented — and an optional flat
 * {@link DebtPlanInputs#getDefaultFeePerMonth() default fee} is added to each.</p>
 *
 * <p>The monthly rate is the nominal {@code annual/12} convention (as
 * {@link io.binarycodes.calculators.loan.service.LoanCalculator}); interest is
 * {@code balance × rate}. Avalanche ranks by ongoing (post-promo) APR, Snowball
 * by original balance, Custom by input order; all rank once. The minimums-only
 * baseline pays each debt exactly its minimum, ignoring the budget, as the
 * yardstick for interest and time saved.</p>
 *
 * <p>Stateless static utility. The only hard error is having no valid debts; an
 * unpayable plan is not an error — it comes back with defaults flagged and, if it
 * never clears within the 100-year cap, {@link DebtScheduleResult#fullyPaid()}
 * false.</p>
 */
public final class DebtCalculator {

    private static final MathContext MC = Rates.CONTEXT;
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final int MONEY_SCALE = 2;

    /** Hard stop at 100 years so a plan that never amortizes can't loop forever. */
    private static final int MONTH_CAP = 1200;

    private DebtCalculator() {
    }

    public static DebtPlanResult calculate(DebtPlanInputs inputs, BigDecimal defaultMinimumFloor) {
        final List<Debt> debts = validDebts(inputs);
        if (debts.isEmpty()) {
            throw new IllegalArgumentException("debt.validation.needDebt");
        }
        final BigDecimal floor = defaultMinimumFloor == null ? BigDecimal.ZERO : defaultMinimumFloor;
        final BigDecimal budget = inputs.getMonthlyBudget() == null
                ? BigDecimal.ZERO : inputs.getMonthlyBudget().max(BigDecimal.ZERO);
        final BigDecimal defaultFee = inputs.getDefaultFeePerMonth() == null
                ? BigDecimal.ZERO : inputs.getDefaultFeePerMonth().max(BigDecimal.ZERO);
        final BigDecimal inflation = Rates.pctToFraction(inputs.getInflationRatePct());
        final PlanBudget planBudget = new PlanBudget(budget,
                Rates.pctToFraction(inputs.getBudgetStepUpPct()), windfallsByMonth(inputs));

        final int calendarYear = Year.now().getValue();

        final DebtScheduleResult avalanche =
                simulate(debts, floor, planBudget, defaultFee, PayoffStrategy.AVALANCHE, true, inflation, calendarYear);
        final DebtScheduleResult snowball =
                simulate(debts, floor, planBudget, defaultFee, PayoffStrategy.SNOWBALL, true, inflation, calendarYear);
        final DebtScheduleResult baseline =
                simulate(debts, floor, PlanBudget.none(), BigDecimal.ZERO, null, false, inflation, calendarYear);

        final PayoffStrategy primaryStrategy =
                inputs.getStrategy() == null ? PayoffStrategy.AVALANCHE : inputs.getStrategy();
        final DebtScheduleResult primary = switch (primaryStrategy) {
            case AVALANCHE -> avalanche;
            case SNOWBALL -> snowball;
            case CUSTOM -> simulate(debts, floor, planBudget, defaultFee,
                    PayoffStrategy.CUSTOM, true, inflation, calendarYear);
        };

        final BigDecimal interestSaved =
                baseline.totalInterest().subtract(primary.totalInterest(), MC).max(BigDecimal.ZERO);
        final BigDecimal realInterestSaved =
                baseline.realTotalInterest().subtract(primary.realTotalInterest(), MC).max(BigDecimal.ZERO);
        final int monthsSaved = Math.max(0, baseline.payoffMonth() - primary.payoffMonth());

        return new DebtPlanResult(primary, avalanche, snowball, baseline, primaryStrategy,
                scale(interestSaved), monthsSaved, scale(realInterestSaved));
    }

    private static DebtScheduleResult simulate(List<Debt> debts, BigDecimal floor, PlanBudget budget,
                                               BigDecimal defaultFee, PayoffStrategy strategy,
                                               boolean budgetLimited, BigDecimal inflation, int calendarYear) {
        final List<WorkingDebt> working = debts.stream().map(debt -> new WorkingDebt(debt, floor)).toList();
        final List<WorkingDebt> order = rank(working, strategy);

        BigDecimal totalInterest = BigDecimal.ZERO;
        BigDecimal realInterest = BigDecimal.ZERO;
        BigDecimal cumulativeInterest = BigDecimal.ZERO;

        final List<DebtPlanYear> years = new ArrayList<>();
        final List<MonthlyPayment> monthlyPayments = new ArrayList<>();
        BigDecimal yearInterest = BigDecimal.ZERO;
        BigDecimal yearPrincipal = BigDecimal.ZERO;
        final YearTargets yearTargets = new YearTargets(order);
        int monthsInYear = 0;
        int yearIndex = 0;

        int month = 0;
        while (anyOutstanding(working) && month < MONTH_CAP) {
            month++;

            BigDecimal interestThisMonth = BigDecimal.ZERO;
            for (final WorkingDebt debt : working) {
                debt.startMonth();
                if (debt.cleared()) {
                    continue;
                }
                final BigDecimal interest = debt.balance.multiply(debt.monthlyRate(month), MC);
                debt.balance = debt.balance.add(interest, MC);
                debt.requiredMinimum = debt.effectiveMinimum(debt.statementBalance).min(debt.balance);
                interestThisMonth = interestThisMonth.add(interest, MC);
            }

            if (budgetLimited) {
                distributeBudget(order, budget.forMonth(month).add(budget.windfall(month), MC), yearTargets);
                applyDefaults(working, defaultFee);
            } else {
                for (final WorkingDebt debt : working) {
                    if (!debt.cleared()) {
                        debt.pay(debt.requiredMinimum);
                    }
                }
            }

            BigDecimal paidThisMonth = BigDecimal.ZERO;
            for (final WorkingDebt debt : working) {
                paidThisMonth = paidThisMonth.add(debt.paidThisMonth, MC);
                if (!debt.cleared() && debt.balance.signum() <= 0) {
                    debt.balance = BigDecimal.ZERO;
                    debt.payoffMonth = month;
                }
            }

            monthlyPayments.add(snapshotMonth(month, working));

            totalInterest = totalInterest.add(interestThisMonth, MC);
            realInterest = realInterest.add(deflate(interestThisMonth, inflation, month), MC);
            cumulativeInterest = cumulativeInterest.add(interestThisMonth, MC);
            yearInterest = yearInterest.add(interestThisMonth, MC);
            yearPrincipal = yearPrincipal.add(paidThisMonth.subtract(interestThisMonth, MC), MC);
            monthsInYear++;

            final boolean allCleared = !anyOutstanding(working);
            if (monthsInYear == 12 || allCleared || month == MONTH_CAP) {
                years.add(new DebtPlanYear(yearIndex + 1, calendarYear + yearIndex + 1,
                        scale(totalOutstanding(working)), scale(yearInterest), scale(yearPrincipal),
                        yearTargets.drain(), scale(cumulativeInterest)));
                yearIndex++;
                monthsInYear = 0;
                yearInterest = BigDecimal.ZERO;
                yearPrincipal = BigDecimal.ZERO;
            }
        }

        final Map<String, Integer> payoffByDebt = new LinkedHashMap<>();
        int payoffMonth = 0;
        boolean fullyPaid = true;
        for (final WorkingDebt debt : working) {
            if (debt.cleared()) {
                payoffByDebt.put(debt.name, debt.payoffMonth);
                payoffMonth = Math.max(payoffMonth, debt.payoffMonth);
            } else {
                payoffByDebt.put(debt.name, MONTH_CAP + 1);
                fullyPaid = false;
            }
        }
        if (!fullyPaid) {
            payoffMonth = MONTH_CAP;
        }

        // Fees push more cash out than principal + interest, so total paid comes
        // from the actual monthly outlays rather than a closed form.
        return new DebtScheduleResult(strategy, years, monthlyPayments, payoffMonth, fullyPaid,
                scale(totalInterest), scale(totalPaidFrom(monthlyPayments)), scale(realInterest), payoffByDebt);
    }

    /** Cover minimums in strategy order, then funnel the remainder to the target, cascading. */
    private static void distributeBudget(List<WorkingDebt> order, BigDecimal available, YearTargets yearTargets) {
        BigDecimal remaining = available.max(BigDecimal.ZERO);
        for (final WorkingDebt debt : order) {
            if (debt.cleared() || remaining.signum() <= 0) {
                continue;
            }
            final BigDecimal pay = debt.requiredMinimum.min(remaining);
            debt.pay(pay);
            remaining = remaining.subtract(pay, MC);
        }
        for (final WorkingDebt debt : order) {
            if (remaining.signum() <= 0) {
                break;
            }
            if (debt.cleared() || debt.balance.signum() <= 0) {
                continue;
            }
            final BigDecimal surplus = remaining.min(debt.balance);
            debt.pay(surplus);
            remaining = remaining.subtract(surplus, MC);
            yearTargets.add(debt.name);
        }
    }

    private static void applyDefaults(List<WorkingDebt> working, BigDecimal defaultFee) {
        for (final WorkingDebt debt : working) {
            if (debt.cleared()) {
                continue;
            }
            if (debt.paidThisMonth.compareTo(debt.requiredMinimum) < 0 && debt.balance.signum() > 0) {
                debt.defaultedThisMonth = true;
                if (defaultFee.signum() > 0) {
                    debt.balance = debt.balance.add(defaultFee, MC);
                }
            }
        }
    }

    private static MonthlyPayment snapshotMonth(int month, List<WorkingDebt> working) {
        final List<DebtPayment> payments = new ArrayList<>(working.size());
        BigDecimal total = BigDecimal.ZERO;
        for (final WorkingDebt debt : working) {
            payments.add(new DebtPayment(debt.name, scale(debt.paidThisMonth), debt.defaultedThisMonth));
            total = total.add(debt.paidThisMonth, MC);
        }
        return new MonthlyPayment(month, payments, scale(total));
    }

    private static BigDecimal totalPaidFrom(List<MonthlyPayment> monthlyPayments) {
        BigDecimal sum = BigDecimal.ZERO;
        for (final MonthlyPayment payment : monthlyPayments) {
            sum = sum.add(payment.total(), MC);
        }
        return sum;
    }

    private static List<WorkingDebt> rank(List<WorkingDebt> working, PayoffStrategy strategy) {
        // The baseline (null) and CUSTOM both pay in the debts' input order — for
        // CUSTOM that is exactly the order the user arranged in the form.
        if (strategy == null || strategy == PayoffStrategy.CUSTOM) {
            return working;
        }
        final Comparator<WorkingDebt> comparator = strategy == PayoffStrategy.SNOWBALL
                ? Comparator.comparing(debt -> debt.originalBalance)
                : Comparator.comparing((WorkingDebt debt) -> debt.ongoingAnnualRate).reversed();
        final List<WorkingDebt> ordered = new ArrayList<>(working);
        // A stable sort preserves input order as the tie-break.
        ordered.sort(comparator);
        return ordered;
    }

    private static Map<Integer, BigDecimal> windfallsByMonth(DebtPlanInputs inputs) {
        if (inputs.getWindfalls() == null) {
            return Map.of();
        }
        final Map<Integer, BigDecimal> byMonth = new HashMap<>();
        for (final Windfall windfall : inputs.getWindfalls()) {
            if (windfall.getMonth() == null || windfall.getMonth() < 1
                    || windfall.getAmount() == null || windfall.getAmount().signum() <= 0) {
                continue;
            }
            byMonth.merge(windfall.getMonth(), windfall.getAmount(), (existing, added) -> existing.add(added, MC));
        }
        return byMonth;
    }

    private static List<Debt> validDebts(DebtPlanInputs inputs) {
        if (inputs.getDebts() == null) {
            return List.of();
        }
        return inputs.getDebts().stream()
                .filter(debt -> debt.getBalance() != null && debt.getBalance().signum() > 0)
                .toList();
    }

    private static boolean anyOutstanding(List<WorkingDebt> working) {
        return working.stream().anyMatch(debt -> !debt.cleared());
    }

    private static BigDecimal totalOutstanding(List<WorkingDebt> working) {
        BigDecimal sum = BigDecimal.ZERO;
        for (final WorkingDebt debt : working) {
            sum = sum.add(debt.balance, MC);
        }
        return sum;
    }

    private static BigDecimal toMonthlyRate(BigDecimal annualFraction) {
        return annualFraction.signum() == 0 ? BigDecimal.ZERO : annualFraction.divide(TWELVE, MC);
    }

    private static BigDecimal deflate(BigDecimal amount, BigDecimal inflation, int monthIndex) {
        if (inflation.signum() <= 0) {
            return amount;
        }
        final BigDecimal deflator = BigDecimal.valueOf(
                Math.pow(1.0 + inflation.doubleValue(), monthIndex / 12.0));
        return amount.divide(deflator, MC);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * The monthly budget the user commits, grown annually by the step-up, with
     * one-off windfalls indexed by their month.
     */
    private record PlanBudget(BigDecimal monthlyBudget, BigDecimal stepUpFraction,
                              Map<Integer, BigDecimal> windfallsByMonth) {

        static PlanBudget none() {
            return new PlanBudget(BigDecimal.ZERO, BigDecimal.ZERO, Map.of());
        }

        BigDecimal forMonth(int month) {
            final int yearsElapsed = (month - 1) / 12;
            return monthlyBudget.multiply(Rates.pow1plus(stepUpFraction, yearsElapsed), MC);
        }

        BigDecimal windfall(int month) {
            return windfallsByMonth.getOrDefault(month, BigDecimal.ZERO);
        }
    }

    /** Mutable per-run debt state; a fresh instance is built for every simulation. */
    private static final class WorkingDebt {

        private final String name;
        private final BigDecimal originalBalance;
        private final BigDecimal ongoingAnnualRate;
        private final BigDecimal standardMonthlyRate;
        private final BigDecimal promoMonthlyRate;
        private final int promoMonths;
        private final BigDecimal floor;
        private final BigDecimal minimumPctFraction;

        private BigDecimal balance;
        private int payoffMonth;

        private BigDecimal statementBalance = BigDecimal.ZERO;
        private BigDecimal requiredMinimum = BigDecimal.ZERO;
        private BigDecimal paidThisMonth = BigDecimal.ZERO;
        private boolean defaultedThisMonth;

        WorkingDebt(Debt debt, BigDecimal defaultFloor) {
            this.name = debt.getName();
            this.originalBalance = debt.getBalance();
            this.ongoingAnnualRate = Rates.pctToFraction(debt.getAprPct());
            this.standardMonthlyRate = toMonthlyRate(this.ongoingAnnualRate);
            this.promoMonthlyRate = toMonthlyRate(Rates.pctToFraction(debt.getPromoAprPct()));
            this.promoMonths = debt.getPromoMonths() == null ? 0 : Math.max(0, debt.getPromoMonths());
            this.floor = debt.getMinimumPayment() == null ? defaultFloor : debt.getMinimumPayment();
            this.minimumPctFraction = Rates.pctToFraction(debt.getMinimumPct());
            this.balance = debt.getBalance();
        }

        void startMonth() {
            this.statementBalance = this.balance;
            this.requiredMinimum = BigDecimal.ZERO;
            this.paidThisMonth = BigDecimal.ZERO;
            this.defaultedThisMonth = false;
        }

        void pay(BigDecimal amount) {
            this.balance = this.balance.subtract(amount, MC);
            this.paidThisMonth = this.paidThisMonth.add(amount, MC);
        }

        BigDecimal monthlyRate(int month) {
            return promoMonths > 0 && month <= promoMonths ? promoMonthlyRate : standardMonthlyRate;
        }

        BigDecimal effectiveMinimum(BigDecimal balanceAtStatement) {
            return floor.max(minimumPctFraction.multiply(balanceAtStatement, MC));
        }

        boolean cleared() {
            return payoffMonth > 0;
        }
    }

    /** Accumulates the debts funnelled to within a year, emitted in strategy order. */
    private static final class YearTargets {

        private final List<WorkingDebt> order;
        private final List<String> touched = new ArrayList<>();

        YearTargets(List<WorkingDebt> order) {
            this.order = order;
        }

        void add(String name) {
            if (!touched.contains(name)) {
                touched.add(name);
            }
        }

        List<String> drain() {
            final List<String> result = order.stream()
                    .map(debt -> debt.name)
                    .filter(touched::contains)
                    .toList();
            touched.clear();
            return result;
        }
    }
}
