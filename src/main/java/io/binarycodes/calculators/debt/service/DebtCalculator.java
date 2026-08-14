package io.binarycodes.calculators.debt.service;

import io.binarycodes.calculators.base.math.Rates;
import io.binarycodes.calculators.debt.domain.Debt;
import io.binarycodes.calculators.debt.domain.DebtPlanInputs;
import io.binarycodes.calculators.debt.domain.DebtPlanResult;
import io.binarycodes.calculators.debt.domain.DebtPlanYear;
import io.binarycodes.calculators.debt.domain.DebtScheduleResult;
import io.binarycodes.calculators.debt.domain.PayoffStrategy;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Month-by-month multi-debt payoff simulator.
 *
 * <p>The monthly rate is the <b>nominal</b> {@code annual/12} convention (the
 * same as {@link io.binarycodes.calculators.loan.service.LoanCalculator}), and
 * interest each month is {@code balance × rate}. Each debt's effective minimum is
 * {@code max(minimumFloor, minimumPct% × statement balance)}, capped at the
 * outstanding balance, where {@code minimumFloor} is the debt's own
 * {@code minimumPayment} when set and the currency-scaled default floor
 * otherwise.</p>
 *
 * <p>The monthly budget is constant: {@code Σ initial effective minimums +
 * extraPerMonth}. Each month every debt accrues interest and pays its minimum;
 * the leftover budget is funnelled to the debts in the strategy's fixed order,
 * cascading within the same month once a target clears. Avalanche ranks by the
 * ongoing (post-promo) APR, Snowball by the original balance; both rank once and
 * keep that order. The minimums-only baseline runs the same loop with no extra
 * and no funnelling.</p>
 *
 * <p>Stateless static utility, mirroring the other calculators. Invalid input is
 * reported by throwing {@link IllegalArgumentException} with a translation key
 * the view surfaces as a form-level message.</p>
 */
public final class DebtCalculator {

    private static final MathContext MC = Rates.CONTEXT;
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final int MONEY_SCALE = 2;

    /** Hard stop at 100 years so a pathological, never-amortizing plan can't loop forever. */
    private static final int MONTH_CAP = 1200;

    private DebtCalculator() {
    }

    public static DebtPlanResult calculate(DebtPlanInputs inputs, BigDecimal defaultMinimumFloor) {
        final List<Debt> debts = validDebts(inputs);
        if (debts.isEmpty()) {
            throw new IllegalArgumentException("debt.validation.needDebt");
        }
        final BigDecimal floor = defaultMinimumFloor == null ? BigDecimal.ZERO : defaultMinimumFloor;
        final BigDecimal extra = inputs.getExtraPerMonth() == null
                ? BigDecimal.ZERO : inputs.getExtraPerMonth().max(BigDecimal.ZERO);
        final BigDecimal inflation = Rates.pctToFraction(inputs.getInflationRatePct());

        final BigDecimal budget = totalInitialMinimums(debts, floor).add(extra, MC);
        if (budget.compareTo(firstMonthInterest(debts)) <= 0) {
            throw new IllegalArgumentException("debt.warning.infeasible");
        }

        final int calendarYear = Year.now().getValue();

        final DebtScheduleResult avalanche =
                simulate(debts, floor, budget, PayoffStrategy.AVALANCHE, true, inflation, calendarYear);
        final DebtScheduleResult snowball =
                simulate(debts, floor, budget, PayoffStrategy.SNOWBALL, true, inflation, calendarYear);
        final DebtScheduleResult baseline =
                simulate(debts, floor, BigDecimal.ZERO, null, false, inflation, calendarYear);

        final PayoffStrategy primaryStrategy =
                inputs.getStrategy() == null ? PayoffStrategy.AVALANCHE : inputs.getStrategy();
        final DebtScheduleResult primary =
                primaryStrategy == PayoffStrategy.SNOWBALL ? snowball : avalanche;

        final BigDecimal interestSaved =
                baseline.totalInterest().subtract(primary.totalInterest(), MC).max(BigDecimal.ZERO);
        final BigDecimal realInterestSaved =
                baseline.realTotalInterest().subtract(primary.realTotalInterest(), MC).max(BigDecimal.ZERO);
        final int monthsSaved = Math.max(0, baseline.payoffMonth() - primary.payoffMonth());

        return new DebtPlanResult(avalanche, snowball, baseline, primaryStrategy,
                scale(interestSaved), monthsSaved, scale(realInterestSaved));
    }

    private static DebtScheduleResult simulate(List<Debt> debts, BigDecimal floor, BigDecimal budget,
                                               PayoffStrategy strategy, boolean funnelSurplus,
                                               BigDecimal inflation, int calendarYear) {
        final List<WorkingDebt> working = debts.stream().map(debt -> new WorkingDebt(debt, floor)).toList();
        final List<WorkingDebt> order = rank(working, strategy);

        BigDecimal totalInterest = BigDecimal.ZERO;
        BigDecimal realInterest = BigDecimal.ZERO;
        BigDecimal cumulativeInterest = BigDecimal.ZERO;

        final List<DebtPlanYear> years = new ArrayList<>();
        BigDecimal yearInterest = BigDecimal.ZERO;
        BigDecimal yearPrincipal = BigDecimal.ZERO;
        final YearTargets yearTargets = new YearTargets(order);
        int monthsInYear = 0;
        int yearIndex = 0;

        int month = 0;
        while (anyOutstanding(working) && month < MONTH_CAP) {
            month++;

            BigDecimal interestThisMonth = BigDecimal.ZERO;
            BigDecimal paidThisMonth = BigDecimal.ZERO;
            for (final WorkingDebt debt : working) {
                if (debt.cleared()) {
                    continue;
                }
                final BigDecimal statementBalance = debt.balance;
                final BigDecimal interest = statementBalance.multiply(debt.monthlyRate(month), MC);
                debt.balance = debt.balance.add(interest, MC);
                interestThisMonth = interestThisMonth.add(interest, MC);

                final BigDecimal minimum = debt.effectiveMinimum(statementBalance).min(debt.balance);
                debt.balance = debt.balance.subtract(minimum, MC);
                paidThisMonth = paidThisMonth.add(minimum, MC);
            }

            if (funnelSurplus) {
                BigDecimal remaining = budget.subtract(paidThisMonth, MC).max(BigDecimal.ZERO);
                for (final WorkingDebt debt : order) {
                    if (remaining.signum() <= 0) {
                        break;
                    }
                    if (debt.cleared() || debt.balance.signum() <= 0) {
                        continue;
                    }
                    final BigDecimal surplus = remaining.min(debt.balance);
                    debt.balance = debt.balance.subtract(surplus, MC);
                    remaining = remaining.subtract(surplus, MC);
                    paidThisMonth = paidThisMonth.add(surplus, MC);
                    yearTargets.add(debt.name);
                }
            }

            for (final WorkingDebt debt : working) {
                if (!debt.cleared() && debt.balance.signum() <= 0) {
                    debt.balance = BigDecimal.ZERO;
                    debt.payoffMonth = month;
                }
            }

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
        for (final WorkingDebt debt : working) {
            final int cleared = debt.cleared() ? debt.payoffMonth : MONTH_CAP + 1;
            payoffByDebt.put(debt.name, cleared);
            payoffMonth = Math.max(payoffMonth, cleared);
        }
        if (payoffMonth > MONTH_CAP) {
            throw new IllegalArgumentException("debt.warning.notPaidOff");
        }

        final BigDecimal totalPaid = totalInterest.add(totalInitialBalance(debts), MC);
        return new DebtScheduleResult(strategy, years, payoffMonth,
                scale(totalInterest), scale(totalPaid), scale(realInterest), payoffByDebt);
    }

    private static List<WorkingDebt> rank(List<WorkingDebt> working, PayoffStrategy strategy) {
        if (strategy == null) {
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

    private static List<Debt> validDebts(DebtPlanInputs inputs) {
        if (inputs.getDebts() == null) {
            return List.of();
        }
        return inputs.getDebts().stream()
                .filter(debt -> debt.getBalance() != null && debt.getBalance().signum() > 0)
                .toList();
    }

    private static BigDecimal totalInitialMinimums(List<Debt> debts, BigDecimal floor) {
        BigDecimal sum = BigDecimal.ZERO;
        for (final Debt debt : debts) {
            sum = sum.add(new WorkingDebt(debt, floor).effectiveMinimum(debt.getBalance()), MC);
        }
        return sum;
    }

    private static BigDecimal firstMonthInterest(List<Debt> debts) {
        BigDecimal sum = BigDecimal.ZERO;
        for (final Debt debt : debts) {
            sum = sum.add(debt.getBalance().multiply(toMonthlyRate(promoOrStandard(debt, 1)), MC), MC);
        }
        return sum;
    }

    private static BigDecimal totalInitialBalance(List<Debt> debts) {
        BigDecimal sum = BigDecimal.ZERO;
        for (final Debt debt : debts) {
            sum = sum.add(debt.getBalance(), MC);
        }
        return sum;
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

    private static BigDecimal promoOrStandard(Debt debt, int month) {
        final boolean inPromo = debt.getPromoMonths() != null && debt.getPromoMonths() > 0
                && month <= debt.getPromoMonths();
        return inPromo ? Rates.pctToFraction(debt.getPromoAprPct()) : Rates.pctToFraction(debt.getAprPct());
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

        BigDecimal monthlyRate(int month) {
            return promoMonths > 0 && month <= promoMonths ? promoMonthlyRate : standardMonthlyRate;
        }

        BigDecimal effectiveMinimum(BigDecimal statementBalance) {
            return floor.max(minimumPctFraction.multiply(statementBalance, MC));
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
