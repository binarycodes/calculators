package io.binarycodes.calculators.goal.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Per-investment corpus build-up over time, for the "by investment" chart. One
 * of these is produced per {@link Investment} bucket, in input order.
 *
 * <p>The two balance lists mirror {@link GoalResult}'s x-axis sources so the
 * chart can plot a line per bucket without re-deriving the timeline:
 * {@code yearlyBalances} aligns 1:1 with {@link GoalResult#rows()} and
 * {@code monthlyBalances} aligns 1:1 with {@link GoalResult#monthlySnapshots()}
 * (the latter is empty for long horizons, exactly as the snapshots are).</p>
 *
 * @param label          the bucket's user label; may be {@code null}/blank
 *                       (the chart falls back to a positional name)
 * @param yearlyBalances end-of-year balance for this bucket, per projection row
 * @param monthlyBalances end-of-month balance for this bucket, per monthly
 *                        snapshot; empty when the horizon has no monthly data
 */
public record InvestmentSeries(
        String label,
        List<BigDecimal> yearlyBalances,
        List<BigDecimal> monthlyBalances
) {
}
