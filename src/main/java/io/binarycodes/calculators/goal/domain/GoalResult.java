package io.binarycodes.calculators.goal.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Aggregate output of {@code GoalCalculator.calculate(...)}.
 *
 * @param monthlyInvestment        monthly SIP required in the first year
 * @param firstYearInvestment      yearly investment in the first year ({@code monthlyInvestment × 12})
 * @param totalMonths              total horizon length in months
 * @param inflatedGoal             goal amount inflated forward to the horizon year
 *                                 (today's-money goal × {@code (1+inflation)^years})
 * @param finalBalance             gross corpus at the goal year before tax
 * @param finalPrincipal           cumulative principal at the goal year
 * @param finalGains               {@code finalBalance − finalPrincipal}
 * @param taxAtExit                tax due on the gains portion if everything is withdrawn
 * @param netAtExit                {@code finalBalance − taxAtExit} (matches the goal when solved)
 * @param goalAlreadyCovered       {@code true} when the current corpus alone (net of tax)
 *                                 already reaches the goal — monthly contribution is zero
 * @param rows                     per-year projection rows (last row may cover a partial year
 *                                 when the horizon is not a whole number of years)
 * @param monthlySnapshots         per-month snapshots populated only when the horizon is short
 *                                 enough that a yearly chart would be too sparse to be useful
 *                                 ({@code totalMonths < 36}); empty list otherwise
 * @param investmentSeries         one per investment bucket (input order): its corpus
 *                                 build-up over time, aligned to {@code rows} and
 *                                 {@code monthlySnapshots}. Drives the by-investment chart.
 */
public record GoalResult(
        BigDecimal monthlyInvestment,
        BigDecimal firstYearInvestment,
        int totalMonths,
        BigDecimal inflatedGoal,
        BigDecimal finalBalance,
        BigDecimal finalPrincipal,
        BigDecimal finalGains,
        BigDecimal taxAtExit,
        BigDecimal netAtExit,
        boolean goalAlreadyCovered,
        List<GoalProjectionRow> rows,
        List<MonthSnapshot> monthlySnapshots,
        List<InvestmentSeries> investmentSeries
) {
    public int yearsToGoal() {
        return totalMonths / 12;
    }

    public int remainderMonths() {
        return totalMonths % 12;
    }
}
