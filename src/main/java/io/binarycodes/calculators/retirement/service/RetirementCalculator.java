package io.binarycodes.calculators.retirement.service;

import io.binarycodes.calculators.retirement.domain.FutureExpense;
import io.binarycodes.calculators.retirement.domain.FutureIncome;
import io.binarycodes.calculators.retirement.domain.ProjectionRow;
import io.binarycodes.calculators.retirement.domain.RetirementBenefit;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;
import io.binarycodes.calculators.retirement.domain.RetirementResult;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Arithmetic uses {@link BigDecimal} under {@link MathContext#DECIMAL64} —
 * matches IEEE 754 double precision while avoiding rounding drift on equality
 * assertions.
 *
 * <p>The calculator models two persistent buckets that each track their own
 * principal and gains:</p>
 *
 * <ul>
 *     <li><b>Main corpus</b> — seeded with the user's existing corpus, grows
 *         at {@code growthPre}/{@code growthPost}, taxed at
 *         {@code corpusTaxRate} on gains.</li>
 *     <li><b>SIP corpus</b> — seeded at zero, accumulates monthly
 *         contributions plus retirement benefits and future incomes. Grows
 *         at {@code sipGrowthPre}/{@code sipGrowthPost}. Gains taxed at the
 *         current phase's {@code taxRatePre}/{@code taxRatePost}.</li>
 * </ul>
 *
 * <p>Each year's withdrawal is satisfied by draining the bucket with the
 * lower current growth rate first, so the higher-yielding bucket compounds
 * longer. When a bucket is drawn down, the gains portion of the draw
 * incurs tax (reported separately; it doesn't reduce the gross corpus
 * drawdown).</p>
 */
public final class RetirementCalculator {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    private RetirementCalculator() {
    }

    public static RetirementResult calculate(RetirementInputs in) {
        if (in.getCurrentAge() >= in.getRetireAge()) {
            throw new IllegalArgumentException("Retirement age must be greater than current age.");
        }
        if (in.getRetireAge() >= in.getLifeExp()) {
            throw new IllegalArgumentException("Life expectancy must be greater than retirement age.");
        }

        final BigDecimal inflation = pctToFraction(in.getInflationPct());
        final BigDecimal growthPre = pctToFraction(in.getGrowthPrePct());
        final BigDecimal growthPost = pctToFraction(in.getGrowthPostPct());
        final BigDecimal corpusTaxRate = pctToFraction(in.getCorpusTaxRatePct());
        final BigDecimal sipGrowthPre = pctToFraction(in.getSipGrowthPrePct());
        final BigDecimal sipGrowthPost = pctToFraction(in.getSipGrowthPostPct());
        final BigDecimal sipStepUpPre = pctToFraction(in.getSipStepUpPrePct());
        final BigDecimal sipStepUpPost = pctToFraction(in.getSipStepUpPostPct());
        final BigDecimal sipTaxRatePre = pctToFraction(in.getTaxRatePrePct());
        final BigDecimal sipTaxRatePost = pctToFraction(in.getTaxRatePostPct());
        final BigDecimal annualExp0 = in.getMonthlyExpenses().multiply(TWELVE, MC);
        final BigDecimal annualInvPre = in.getMonthlyInvPre().multiply(TWELVE, MC);
        final BigDecimal annualInvPost = in.getMonthlyInvPost().multiply(TWELVE, MC);

        final Bucket main = new Bucket(in.getCorpus(), in.getCorpus());
        final Bucket sip = new Bucket(BigDecimal.ZERO, BigDecimal.ZERO);

        final List<ProjectionRow> rows = new ArrayList<>();
        BigDecimal totalInvested = in.getCorpus();
        BigDecimal investedAtRetirement = BigDecimal.ZERO;
        Integer corpusDepletedAt = null;
        final int currentYear = Year.now().getValue();

        for (int age = in.getCurrentAge(); age <= in.getLifeExp(); age++) {
            final int yearsFromNow = age - in.getCurrentAge();
            final int year = currentYear + yearsFromNow;
            final boolean isRetireYear = age == in.getRetireAge();
            final boolean isPost = age >= in.getRetireAge();
            final BigDecimal annualExp = annualExp0.multiply(pow1plus(inflation, yearsFromNow), MC);

            if (isRetireYear) {
                investedAtRetirement = totalInvested;
            }

            final BigDecimal mainRate = isPost ? growthPost : growthPre;
            final BigDecimal sipRate = isPost ? sipGrowthPost : sipGrowthPre;
            final BigDecimal sipTaxRate = isPost ? sipTaxRatePost : sipTaxRatePre;

            // This year's investment = SIP contribution (step-up applied) +
            // any retirement benefits received this year + future incomes due
            // this year. All land as principal in the SIP bucket.
            final int yearsInPhase = isPost ? age - in.getRetireAge() : age - in.getCurrentAge();
            final BigDecimal stepUpFactor = pow1plus(isPost ? sipStepUpPost : sipStepUpPre, yearsInPhase);
            final BigDecimal sipContribution = (isPost ? annualInvPost : annualInvPre).multiply(stepUpFactor, MC);
            final BigDecimal netBenefitsThisYear = isRetireYear
                    ? netRetirementBenefits(in.getRetirementBenefits())
                    : BigDecimal.ZERO;
            final BigDecimal netFutureIncomeThisYear = netFutureIncomesFor(
                    in.getFutureIncomes(), year);
            final BigDecimal investment = sipContribution
                    .add(netBenefitsThisYear, MC)
                    .add(netFutureIncomeThisYear, MC);
            totalInvested = totalInvested.add(investment, MC);
            sip.contribute(investment);

            final BigDecimal startCorpus = main.balance.add(sip.balance, MC);

            // Apply this year's growth before any withdrawal.
            final BigDecimal mainReturns = main.applyGrowth(mainRate);
            final BigDecimal sipReturns = sip.applyGrowth(sipRate);
            final BigDecimal returns = mainReturns.add(sipReturns, MC);

            final BigDecimal futureExpensesThisYear = inflatedFutureExpensesFor(
                    in.getFutureExpenses(), year, currentYear);
            final BigDecimal withdrawal = (isPost ? annualExp : BigDecimal.ZERO)
                    .add(futureExpensesThisYear, MC);

            // Drain from the bucket with the lower current growth rate first;
            // tie-break in favour of main. This preserves the higher-yielding
            // bucket for longer compounding.
            final List<BucketDraw> drawOrder = new ArrayList<>();
            drawOrder.add(new BucketDraw(main, mainRate, corpusTaxRate));
            drawOrder.add(new BucketDraw(sip, sipRate, sipTaxRate));
            drawOrder.sort(Comparator.comparing((BucketDraw d) -> d.growthRate));

            BigDecimal remaining = withdrawal;
            BigDecimal taxPaid = BigDecimal.ZERO;
            for (final BucketDraw d : drawOrder) {
                if (remaining.signum() <= 0) {
                    break;
                }
                final BigDecimal balanceBefore = d.bucket.balance;
                final BigDecimal principalBefore = d.bucket.principal;
                remaining = d.bucket.drain(remaining);
                final BigDecimal drawn = balanceBefore.subtract(d.bucket.balance, MC);
                if (drawn.signum() > 0 && balanceBefore.signum() > 0) {
                    final BigDecimal gainsBefore = balanceBefore.subtract(principalBefore, MC);
                    final BigDecimal gainsDrawn = drawn.multiply(gainsBefore, MC)
                            .divide(balanceBefore, MC);
                    if (gainsDrawn.signum() > 0) {
                        taxPaid = taxPaid.add(gainsDrawn.multiply(d.taxRate, MC), MC);
                    }
                }
            }

            final BigDecimal endCorpus;
            final boolean depleted;
            if (remaining.signum() > 0) {
                // Shortfall — surface as negative endCorpus.
                endCorpus = remaining.negate();
                depleted = true;
            } else {
                endCorpus = main.balance.add(sip.balance, MC);
                depleted = endCorpus.signum() < 0;
            }

            rows.add(new ProjectionRow(year, age, isRetireYear, isPost,
                    annualExp, startCorpus, returns, investment, withdrawal,
                    taxPaid, endCorpus, depleted));

            if (depleted && corpusDepletedAt == null) {
                corpusDepletedAt = age;
            }
            if (depleted) {
                break;
            }
        }

        return new RetirementResult(
                List.copyOf(rows),
                Optional.ofNullable(corpusDepletedAt),
                investedAtRetirement);
    }

    /**
     * Mutable per-bucket state. Tracks total balance and principal; gains
     * are derived as {@code balance − principal}.
     */
    private static final class Bucket {
        BigDecimal balance;
        BigDecimal principal;

        Bucket(BigDecimal balance, BigDecimal principal) {
            this.balance = balance;
            this.principal = principal;
        }

        void contribute(BigDecimal amount) {
            this.balance = this.balance.add(amount, MC);
            this.principal = this.principal.add(amount, MC);
        }

        BigDecimal applyGrowth(BigDecimal rate) {
            final BigDecimal earnings = this.balance.multiply(rate, MC);
            this.balance = this.balance.add(earnings, MC);
            return earnings;
        }

        /**
         * Draw up to {@code request} from this bucket. Returns whatever
         * couldn't be satisfied (so the caller can roll over to the next
         * bucket). Updates principal proportionally so the gains-vs-principal
         * mix of the remaining balance is preserved.
         */
        BigDecimal drain(BigDecimal request) {
            if (request.signum() <= 0 || this.balance.signum() <= 0) {
                return request;
            }
            if (request.compareTo(this.balance) >= 0) {
                final BigDecimal shortfall = request.subtract(this.balance, MC);
                this.balance = BigDecimal.ZERO;
                this.principal = BigDecimal.ZERO;
                return shortfall;
            }
            final BigDecimal principalDrawn = request.multiply(this.principal, MC)
                    .divide(this.balance, MC);
            this.principal = this.principal.subtract(principalDrawn, MC);
            this.balance = this.balance.subtract(request, MC);
            return BigDecimal.ZERO;
        }
    }

    private record BucketDraw(Bucket bucket, BigDecimal growthRate, BigDecimal taxRate) {
    }

    private static BigDecimal netRetirementBenefits(List<RetirementBenefit> benefits) {
        if (benefits == null || benefits.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (final RetirementBenefit benefit : benefits) {
            if (benefit == null) {
                continue;
            }
            final BigDecimal amount = benefit.getAmount();
            if (amount == null || amount.signum() <= 0) {
                continue;
            }
            final BigDecimal taxRate = pctToFraction(benefit.getTaxRatePct());
            final BigDecimal net = amount.multiply(BigDecimal.ONE.subtract(taxRate, MC), MC);
            sum = sum.add(net, MC);
        }
        return sum;
    }

    private static BigDecimal netFutureIncomesFor(List<FutureIncome> incomes, int year) {
        if (incomes == null || incomes.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (final FutureIncome income : incomes) {
            if (income == null || !Objects.equals(income.getYear(), year)) {
                continue;
            }
            final BigDecimal amount = income.getAmount();
            if (amount == null || amount.signum() <= 0) {
                continue;
            }
            final BigDecimal taxRate = pctToFraction(income.getTaxRatePct());
            final BigDecimal net = amount.multiply(BigDecimal.ONE.subtract(taxRate, MC), MC);
            sum = sum.add(net, MC);
        }
        return sum;
    }

    private static BigDecimal inflatedFutureExpensesFor(List<FutureExpense> expenses,
                                                        int year, int currentYear) {
        if (expenses == null || expenses.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (final FutureExpense expense : expenses) {
            if (expense == null || !Objects.equals(expense.getYear(), year)) {
                continue;
            }
            final BigDecimal amount = expense.getAmount();
            if (amount == null || amount.signum() <= 0) {
                continue;
            }
            final BigDecimal itemInflation = pctToFraction(expense.getInflationPct());
            final int yearsFromNow = Math.max(0, year - currentYear);
            sum = sum.add(amount.multiply(pow1plus(itemInflation, yearsFromNow), MC), MC);
        }
        return sum;
    }

    private static BigDecimal pctToFraction(BigDecimal pct) {
        return pct == null ? BigDecimal.ZERO : pct.divide(HUNDRED, MC);
    }

    private static BigDecimal pow1plus(BigDecimal rate, int n) {
        if (n == 0) {
            return BigDecimal.ONE;
        }
        final BigDecimal base = BigDecimal.ONE.add(rate, MC);
        BigDecimal acc = BigDecimal.ONE;
        for (int i = 0; i < n; i++) {
            acc = acc.multiply(base, MC);
        }
        return acc;
    }
}
