package io.binarycodes.calculators.retirement.service;

import io.binarycodes.calculators.retirement.domain.ProjectionRow;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;
import io.binarycodes.calculators.retirement.domain.RetirementResult;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pure port of {@code calculate()} from {@code retirement-calculator.js:500-586}.
 *
 * <p>All arithmetic uses {@link BigDecimal} under {@link MathContext#DECIMAL64}
 * (16 significant digits) — matches IEEE 754 double precision used by the JS
 * version while avoiding subtle rounding drift on equality assertions.</p>
 */
public final class RetirementCalculator {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    private RetirementCalculator() {
    }

    public static RetirementResult calculate(RetirementInputs in) {
        if (in.currentAge() >= in.retireAge()) {
            throw new IllegalArgumentException("Retirement age must be greater than current age.");
        }
        if (in.retireAge() >= in.lifeExp()) {
            throw new IllegalArgumentException("Life expectancy must be greater than retirement age.");
        }

        final BigDecimal inflation = pctToFraction(in.inflationPct());
        final BigDecimal growthPre = pctToFraction(in.growthPrePct());
        final BigDecimal growthPost = pctToFraction(in.growthPostPct());
        final BigDecimal sipGrowthPre = pctToFraction(in.sipGrowthPrePct());
        final BigDecimal sipGrowthPost = pctToFraction(in.sipGrowthPostPct());
        final BigDecimal annualExp0 = in.monthlyExpenses().multiply(TWELVE, MC);
        final BigDecimal annualInvPre = in.monthlyInvPre().multiply(TWELVE, MC);
        final BigDecimal annualInvPost = in.monthlyInvPost().multiply(TWELVE, MC);

        final List<ProjectionRow> rows = new ArrayList<>();
        BigDecimal mainCorpus = in.corpus();
        BigDecimal sipCorpus = BigDecimal.ZERO;
        BigDecimal totalInvested = in.corpus();   // initial principal + cumulative SIP
        BigDecimal investedAtRetirement = BigDecimal.ZERO;
        Integer corpusDepletedAt = null;
        final int currentYear = Year.now().getValue();

        for (int age = in.currentAge(); age <= in.lifeExp(); age++) {
            final int yearsFromNow = age - in.currentAge();
            final int year = currentYear + yearsFromNow;
            final boolean isRetireYear = age == in.retireAge();
            final boolean isPost = age >= in.retireAge();
            final BigDecimal annualExp = annualExp0.multiply(pow1plus(inflation, yearsFromNow), MC);

            // At retirement, fold the SIP-accumulated corpus into the main corpus.
            if (isRetireYear) {
                investedAtRetirement = totalInvested;
                mainCorpus = mainCorpus.add(sipCorpus, MC);
                sipCorpus = BigDecimal.ZERO;
            }

            final BigDecimal mainRate = isPost ? growthPost : growthPre;
            final BigDecimal sipRate = isPost ? sipGrowthPost : sipGrowthPre;
            final BigDecimal investment = isPost ? annualInvPost : annualInvPre;
            totalInvested = totalInvested.add(investment, MC);

            final BigDecimal startCorpus = mainCorpus.add(sipCorpus, MC);
            final BigDecimal mainReturns = mainCorpus.multiply(mainRate, MC);
            final BigDecimal sipReturns = sipCorpus.multiply(sipRate, MC);
            final BigDecimal returns = mainReturns.add(sipReturns, MC);

            BigDecimal mainAfter = mainCorpus.add(mainReturns, MC);
            BigDecimal sipAfter = sipCorpus.add(sipReturns, MC).add(investment, MC);

            final BigDecimal withdrawal = isPost ? annualExp : BigDecimal.ZERO;
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

    private static BigDecimal pctToFraction(BigDecimal pct) {
        return pct == null ? BigDecimal.ZERO : pct.divide(HUNDRED, MC);
    }

    /**
     * (1 + rate)^n — used for expense inflation.
     */
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
