package io.binarycodes.calculators.goal.service;

import io.binarycodes.calculators.base.math.Rates;
import io.binarycodes.calculators.goal.domain.GoalInputs;
import io.binarycodes.calculators.goal.domain.GoalProjectionRow;
import io.binarycodes.calculators.goal.domain.GoalResult;
import io.binarycodes.calculators.goal.domain.Investment;
import io.binarycodes.calculators.goal.domain.MonthSnapshot;
import io.binarycodes.calculators.goal.domain.TimeHorizonMode;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Solves "how much do I need to invest each month to reach a goal?" across one
 * or more investment buckets.
 *
 * <p>Each {@link Investment} carries its own corpus, monthly growth rate, exit
 * tax, allocation share, and yearly step-up. The monthly contribution flowing
 * into bucket {@code i} at month {@code m} is</p>
 *
 * <pre>
 *   M · a_i · (1 + s_i)^floor(m/12)
 * </pre>
 *
 * <p>Bucket-level step-up means contributions ramp at different speeds across
 * buckets (e.g. equity ramping while debt stays flat). The net-at-exit (gross
 * balance minus tax on gains) is still linear in {@code M} across all buckets,
 * so the required SIP is solved in closed form:</p>
 *
 * <pre>
 * Σ_i net_corpus_FV_i + M · Σ_i (a_i · netPerM_i) = Goal
 *
 * where, with monthly rate g_m,i = (1 + g_i)^(1/12) − 1 and total months N_m:
 *   corpus_FV_i        = C_i · (1 + g_m,i)^N_m
 *   net_corpus_FV_i    = corpus_FV_i − (corpus_FV_i − C_i) · t_i
 *   α_i                = Σ_{m=0..N_m-1} (1 + s_i)^floor(m/12)
 *   β_i                = Σ_{m=0..N_m-1} (1 + s_i)^floor(m/12) · (1 + g_m,i)^(N_m − m)
 *   netPerM_i          = β_i · (1 − t_i) + α_i · t_i
 *   M = max(0, (Goal − Σ_i net_corpus_FV_i) / Σ_i (a_i · netPerM_i))
 * </pre>
 */
public final class GoalCalculator {

    private static final MathContext MC = Rates.CONTEXT;
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final BigDecimal ALLOCATION_TOLERANCE = new BigDecimal("0.01");
    /**
     * Horizons up to (but not including) this many months render a monthly
     * chart series so the build-up is readable; longer horizons aggregate to
     * yearly data points.
     */
    private static final int MONTHLY_CHART_THRESHOLD = 36;
    private static final DateTimeFormatter MONTH_LABEL_FORMAT =
            DateTimeFormatter.ofPattern("MMM ''yy", Locale.ENGLISH);

    private GoalCalculator() {
    }

    public static GoalResult calculate(GoalInputs inputs) {
        final int totalMonths = resolveTotalMonths(inputs);
        if (totalMonths < 1) {
            throw new IllegalArgumentException("Time to goal must be at least one month.");
        }
        final BigDecimal goalAmount = required(inputs.getGoalAmount(), "Goal amount");
        if (goalAmount.signum() <= 0) {
            throw new IllegalArgumentException("Goal amount must be positive.");
        }
        final List<Investment> investments = inputs.getInvestments();
        if (investments == null || investments.isEmpty()) {
            throw new IllegalArgumentException("Add at least one investment.");
        }
        validateAllocations(investments);

        final List<Bucket> buckets = new ArrayList<>(investments.size());
        for (final Investment investment : investments) {
            final BigDecimal corpus = investment.getCurrentCorpus() == null
                    ? BigDecimal.ZERO
                    : investment.getCurrentCorpus();
            if (corpus.signum() < 0) {
                throw new IllegalArgumentException("Each investment's corpus must be non-negative.");
            }
            final BigDecimal annualGrowth = Rates.pctToFraction(investment.getGrowthRatePct());
            final BigDecimal monthlyGrowth = Rates.monthlyFromAnnual(annualGrowth);
            final BigDecimal taxRate = Rates.pctToFraction(investment.getWithdrawalTaxRatePct());
            final BigDecimal allocation = Rates.pctToFraction(investment.getAllocationPct());
            final BigDecimal stepUp = Rates.pctToFraction(investment.getStepUpPct());
            final BigDecimal grossFv = corpus.multiply(Rates.pow1plus(monthlyGrowth, totalMonths), MC);

            BigDecimal alpha = BigDecimal.ZERO;
            BigDecimal beta = BigDecimal.ZERO;
            for (int monthIndex = 0; monthIndex < totalMonths; monthIndex++) {
                final BigDecimal stepFactor = Rates.pow1plus(stepUp, monthIndex / 12);
                final BigDecimal growthFactor = Rates.pow1plus(monthlyGrowth, totalMonths - monthIndex);
                alpha = alpha.add(stepFactor, MC);
                beta = beta.add(stepFactor.multiply(growthFactor, MC), MC);
            }
            buckets.add(new Bucket(corpus, monthlyGrowth, taxRate, allocation, stepUp,
                    grossFv, alpha, beta));
        }

        BigDecimal netCorpusFvSum = BigDecimal.ZERO;
        BigDecimal grossCorpusFvSum = BigDecimal.ZERO;
        BigDecimal sumWeightedNetPerM = BigDecimal.ZERO;
        for (final Bucket bucket : buckets) {
            final BigDecimal corpusGain = bucket.grossFv.subtract(bucket.corpus, MC);
            final BigDecimal netCorpusFv = bucket.grossFv.subtract(
                    corpusGain.multiply(bucket.taxRate, MC), MC);
            netCorpusFvSum = netCorpusFvSum.add(netCorpusFv, MC);
            grossCorpusFvSum = grossCorpusFvSum.add(bucket.grossFv, MC);

            final BigDecimal netPerM = bucket.beta.multiply(
                    BigDecimal.ONE.subtract(bucket.taxRate, MC), MC)
                    .add(bucket.alpha.multiply(bucket.taxRate, MC), MC);
            sumWeightedNetPerM = sumWeightedNetPerM.add(
                    bucket.allocation.multiply(netPerM, MC), MC);
        }

        if (netCorpusFvSum.compareTo(goalAmount) >= 0) {
            final Projection coveredProjection = buildProjection(inputs, buckets, totalMonths, BigDecimal.ZERO);
            final BigDecimal taxAtExit = computeTaxAtExit(buckets);
            return new GoalResult(
                    BigDecimal.ZERO, BigDecimal.ZERO, totalMonths,
                    grossCorpusFvSum, sumPrincipal(buckets),
                    grossCorpusFvSum.subtract(sumPrincipal(buckets), MC),
                    taxAtExit, netCorpusFvSum,
                    true, coveredProjection.rows(), coveredProjection.monthlySnapshots());
        }

        if (sumWeightedNetPerM.signum() <= 0) {
            throw new IllegalArgumentException("Solver degenerate: growth, allocations, and horizon yield zero leverage.");
        }
        final BigDecimal monthly = goalAmount.subtract(netCorpusFvSum, MC)
                .divide(sumWeightedNetPerM, MC);
        final BigDecimal firstYearInvestment = monthly.multiply(TWELVE, MC);

        final Projection projection = buildProjection(inputs, buckets, totalMonths, monthly);
        final GoalProjectionRow finalRow = projection.rows().get(projection.rows().size() - 1);

        final BigDecimal taxAtExit = computeTaxAtExit(buckets);
        final BigDecimal netAtExit = finalRow.balance().subtract(taxAtExit, MC);

        return new GoalResult(
                monthly, firstYearInvestment, totalMonths,
                finalRow.balance(), finalRow.principal(), finalRow.gains(),
                taxAtExit, netAtExit,
                false,
                projection.rows(), projection.monthlySnapshots());
    }

    private record Projection(List<GoalProjectionRow> rows, List<MonthSnapshot> monthlySnapshots) {
    }

    private static BigDecimal computeTaxAtExit(List<Bucket> buckets) {
        BigDecimal taxAtExit = BigDecimal.ZERO;
        for (final Bucket bucket : buckets) {
            final BigDecimal gains = bucket.balance.subtract(bucket.principal, MC);
            if (gains.signum() > 0) {
                taxAtExit = taxAtExit.add(gains.multiply(bucket.taxRate, MC), MC);
            }
        }
        return taxAtExit;
    }

    private static BigDecimal sumPrincipal(List<Bucket> buckets) {
        BigDecimal sum = BigDecimal.ZERO;
        for (final Bucket bucket : buckets) {
            sum = sum.add(bucket.principal, MC);
        }
        return sum;
    }

    private static void validateAllocations(List<Investment> investments) {
        BigDecimal sum = BigDecimal.ZERO;
        for (final Investment investment : investments) {
            final BigDecimal allocation = investment.getAllocationPct();
            if (allocation == null) {
                throw new IllegalArgumentException("Every investment needs an allocation percentage.");
            }
            if (allocation.signum() < 0) {
                throw new IllegalArgumentException("Allocation percentages must be non-negative.");
            }
            sum = sum.add(allocation);
        }
        if (sum.subtract(BigDecimal.valueOf(100)).abs().compareTo(ALLOCATION_TOLERANCE) > 0) {
            throw new IllegalArgumentException(
                    "Allocation percentages must sum to 100 (got " + sum.toPlainString() + ").");
        }
    }

    public static int resolveTotalMonths(GoalInputs inputs) {
        final TimeHorizonMode mode = inputs.getHorizonMode() == null
                ? TimeHorizonMode.YEARS
                : inputs.getHorizonMode();
        return switch (mode) {
            case YEARS -> {
                final int years = required(inputs.getYearsToGoal(), "Years to goal");
                final int extraMonths = inputs.getMonthsToGoal() == null
                        ? 0
                        : inputs.getMonthsToGoal();
                if (extraMonths < 0 || extraMonths > 11) {
                    throw new IllegalArgumentException("Months must be between 0 and 11.");
                }
                yield years * 12 + extraMonths;
            }
            case AGES -> {
                final int currentAge = required(inputs.getCurrentAge(), "Current age");
                final int goalAge = required(inputs.getGoalAge(), "Goal age");
                yield (goalAge - currentAge) * 12;
            }
            case TARGET_YEAR -> {
                final int targetYear = required(inputs.getTargetYear(), "Target year");
                final int targetMonth = inputs.getTargetMonth() == null
                        ? LocalDate.now().getMonthValue()
                        : inputs.getTargetMonth();
                if (targetMonth < 1 || targetMonth > 12) {
                    throw new IllegalArgumentException("Target month must be between 1 and 12.");
                }
                final LocalDate today = LocalDate.now();
                yield (targetYear - today.getYear()) * 12 + (targetMonth - today.getMonthValue());
            }
        };
    }

    private static Projection buildProjection(GoalInputs inputs,
                                              List<Bucket> buckets,
                                              int totalMonths,
                                              BigDecimal monthly) {
        final int currentYear = Year.now().getValue();
        final Integer currentAge = ageBaseFor(inputs);
        final boolean captureMonthly = totalMonths < MONTHLY_CHART_THRESHOLD;
        final LocalDate today = LocalDate.now();
        final List<GoalProjectionRow> rows = new ArrayList<>();
        final List<MonthSnapshot> monthlySnapshots = new ArrayList<>();

        BigDecimal yearContribution = BigDecimal.ZERO;
        int monthsInCurrentYear = 0;
        int yearIndex = 0;

        for (int monthIndex = 0; monthIndex < totalMonths; monthIndex++) {
            for (final Bucket bucket : buckets) {
                final BigDecimal stepFactor = Rates.pow1plus(bucket.stepUp, monthIndex / 12);
                final BigDecimal share = monthly.multiply(bucket.allocation, MC)
                        .multiply(stepFactor, MC);
                bucket.balance = bucket.balance.add(share, MC);
                bucket.principal = bucket.principal.add(share, MC);
                bucket.balance = bucket.balance.add(
                        bucket.balance.multiply(bucket.monthlyGrowth, MC), MC);
                yearContribution = yearContribution.add(share, MC);
            }
            monthsInCurrentYear++;

            if (captureMonthly) {
                BigDecimal monthlyBalance = BigDecimal.ZERO;
                BigDecimal monthlyPrincipal = BigDecimal.ZERO;
                for (final Bucket bucket : buckets) {
                    monthlyBalance = monthlyBalance.add(bucket.balance, MC);
                    monthlyPrincipal = monthlyPrincipal.add(bucket.principal, MC);
                }
                final LocalDate periodEnd = today.plusMonths(monthIndex + 1L);
                monthlySnapshots.add(new MonthSnapshot(
                        monthIndex,
                        periodEnd.format(MONTH_LABEL_FORMAT),
                        monthlyBalance,
                        monthlyPrincipal,
                        monthlyBalance.subtract(monthlyPrincipal, MC)));
            }

            final boolean isYearBoundary = monthsInCurrentYear == 12;
            final boolean isHorizonEnd = monthIndex == totalMonths - 1;
            if (isYearBoundary || isHorizonEnd) {
                BigDecimal totalBalance = BigDecimal.ZERO;
                BigDecimal totalPrincipal = BigDecimal.ZERO;
                BigDecimal taxIfWithdrawn = BigDecimal.ZERO;
                for (final Bucket bucket : buckets) {
                    totalBalance = totalBalance.add(bucket.balance, MC);
                    totalPrincipal = totalPrincipal.add(bucket.principal, MC);
                    final BigDecimal bucketGain = bucket.balance.subtract(bucket.principal, MC);
                    if (bucketGain.signum() > 0) {
                        taxIfWithdrawn = taxIfWithdrawn.add(
                                bucketGain.multiply(bucket.taxRate, MC), MC);
                    }
                }
                final BigDecimal gains = totalBalance.subtract(totalPrincipal, MC);
                final Integer age = currentAge == null ? null : currentAge + yearIndex + 1;
                rows.add(new GoalProjectionRow(
                        yearIndex, currentYear + yearIndex + 1, age,
                        monthsInCurrentYear,
                        yearContribution, totalBalance, totalPrincipal, gains,
                        taxIfWithdrawn));
                yearIndex++;
                yearContribution = BigDecimal.ZERO;
                monthsInCurrentYear = 0;
            }
        }
        return new Projection(rows, monthlySnapshots);
    }

    private static Integer ageBaseFor(GoalInputs inputs) {
        if (inputs.getHorizonMode() == TimeHorizonMode.AGES && inputs.getCurrentAge() != null) {
            return inputs.getCurrentAge();
        }
        return null;
    }

    private static int required(Integer value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value;
    }

    private static BigDecimal required(BigDecimal value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value;
    }

    /**
     * Mutable per-bucket state used during projection. Seeded with the
     * investment's corpus; {@code balance} and {@code principal} walk forward
     * as we iterate months. {@code alpha} and {@code beta} are the precomputed
     * solver coefficients for this bucket.
     */
    private static final class Bucket {
        final BigDecimal corpus;
        final BigDecimal monthlyGrowth;
        final BigDecimal taxRate;
        final BigDecimal allocation;
        final BigDecimal stepUp;
        final BigDecimal grossFv;
        final BigDecimal alpha;
        final BigDecimal beta;
        BigDecimal balance;
        BigDecimal principal;

        Bucket(BigDecimal corpus, BigDecimal monthlyGrowth, BigDecimal taxRate,
               BigDecimal allocation, BigDecimal stepUp, BigDecimal grossFv,
               BigDecimal alpha, BigDecimal beta) {
            this.corpus = corpus;
            this.monthlyGrowth = monthlyGrowth;
            this.taxRate = taxRate;
            this.allocation = allocation;
            this.stepUp = stepUp;
            this.grossFv = grossFv;
            this.alpha = alpha;
            this.beta = beta;
            this.balance = corpus;
            this.principal = corpus;
        }
    }
}
