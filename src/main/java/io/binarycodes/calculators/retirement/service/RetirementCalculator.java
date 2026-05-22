package io.binarycodes.calculators.retirement.service;

import io.binarycodes.calculators.retirement.domain.FutureExpense;
import io.binarycodes.calculators.retirement.domain.ProjectionRow;
import io.binarycodes.calculators.retirement.domain.RetirementBenefit;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;
import io.binarycodes.calculators.retirement.domain.RetirementResult;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Arithmetic uses {@link BigDecimal} under {@link MathContext#DECIMAL64} —
 * matches IEEE 754 double precision while avoiding rounding drift on equality
 * assertions.
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
        final BigDecimal sipGrowthPre = pctToFraction(in.getSipGrowthPrePct());
        final BigDecimal sipGrowthPost = pctToFraction(in.getSipGrowthPostPct());
        final BigDecimal sipStepUpPre = pctToFraction(in.getSipStepUpPrePct());
        final BigDecimal sipStepUpPost = pctToFraction(in.getSipStepUpPostPct());
        final BigDecimal annualExp0 = in.getMonthlyExpenses().multiply(TWELVE, MC);
        final BigDecimal annualInvPre = in.getMonthlyInvPre().multiply(TWELVE, MC);
        final BigDecimal annualInvPost = in.getMonthlyInvPost().multiply(TWELVE, MC);

        final List<ProjectionRow> rows = new ArrayList<>();
        BigDecimal mainCorpus = in.getCorpus();
        BigDecimal sipCorpus = BigDecimal.ZERO;
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

            // At retirement, fold the SIP-accumulated corpus into the main corpus.
            if (isRetireYear) {
                investedAtRetirement = totalInvested;
                mainCorpus = mainCorpus.add(sipCorpus, MC);
                sipCorpus = BigDecimal.ZERO;
            }

            final BigDecimal mainRate = isPost ? growthPost : growthPre;
            final BigDecimal sipRate = isPost ? sipGrowthPost : sipGrowthPre;
            final int yearsInPhase = isPost ? age - in.getRetireAge() : age - in.getCurrentAge();
            final BigDecimal stepUpFactor = pow1plus(isPost ? sipStepUpPost : sipStepUpPre, yearsInPhase);
            final BigDecimal investment = (isPost ? annualInvPost : annualInvPre).multiply(stepUpFactor, MC);
            totalInvested = totalInvested.add(investment, MC);

            final BigDecimal startCorpus = mainCorpus.add(sipCorpus, MC);
            final BigDecimal mainReturns = mainCorpus.multiply(mainRate, MC);
            final BigDecimal sipReturns = sipCorpus.multiply(sipRate, MC);
            final BigDecimal returns = mainReturns.add(sipReturns, MC);

            BigDecimal mainAfter = mainCorpus.add(mainReturns, MC);
            BigDecimal sipAfter = sipCorpus.add(sipReturns, MC).add(investment, MC);

            final BigDecimal futureExpensesThisYear = inflatedFutureExpensesFor(
                    in.getFutureExpenses(), year, currentYear);
            final BigDecimal netBenefitsThisYear = isRetireYear
                    ? netRetirementBenefits(in.getRetirementBenefits())
                    : BigDecimal.ZERO;
            final BigDecimal withdrawal = (isPost ? annualExp : BigDecimal.ZERO)
                    .add(futureExpensesThisYear, MC)
                    .subtract(netBenefitsThisYear, MC);
            final BigDecimal endCorpus;

            if (withdrawal.signum() == 0) {
                endCorpus = mainAfter.add(sipAfter, MC);
            } else {
                final BigDecimal pool = mainAfter.add(sipAfter, MC);
                if (pool.compareTo(withdrawal) >= 0) {
                    // proportional draw — both buckets shrink in proportion to their share
                    final BigDecimal mainShare = pool.signum() > 0
                            ? mainAfter.divide(pool, MC)
                            : BigDecimal.ZERO;
                    mainAfter = mainAfter.subtract(withdrawal.multiply(mainShare, MC), MC);
                    sipAfter = sipAfter.subtract(
                            withdrawal.multiply(BigDecimal.ONE.subtract(mainShare, MC), MC), MC);
                    endCorpus = mainAfter.add(sipAfter, MC);
                } else {
                    // shortfall — surface the deficit (negative) so callers can see the gap
                    endCorpus = pool.subtract(withdrawal, MC);
                    mainAfter = BigDecimal.ZERO;
                    sipAfter = BigDecimal.ZERO;
                }
            }

            final boolean depleted = endCorpus.signum() < 0;
            rows.add(new ProjectionRow(year, age, isRetireYear, isPost,
                    annualExp, startCorpus, returns, investment, withdrawal,
                    endCorpus, depleted));

            if (depleted && corpusDepletedAt == null) {
                corpusDepletedAt = age;
            }

            mainCorpus = mainAfter.signum() > 0 ? mainAfter : BigDecimal.ZERO;
            sipCorpus = sipAfter.signum() > 0 ? sipAfter : BigDecimal.ZERO;
            if (depleted) {
                break;
            }
        }

        return new RetirementResult(
                List.copyOf(rows),
                Optional.ofNullable(corpusDepletedAt),
                investedAtRetirement);
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
