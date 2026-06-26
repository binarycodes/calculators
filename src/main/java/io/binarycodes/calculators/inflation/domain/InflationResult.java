package io.binarycodes.calculators.inflation.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Output of {@code InflationCalculator.calculate(...)}.
 *
 * @param totalMonths   horizon length in months
 * @param inputAmount   the amount the user entered
 * @param resultAmount  the amount at the other end of the horizon
 * @param amountIsToday whether {@link #inputAmount} was today's money (forward
 *                      projection) or a future value (backward projection)
 * @param progression   year-by-year nominal value, growing from today's value
 *                      at the inflation rate — drives the value-over-time chart
 * @param band          year-by-year low/high values under the inflation rate
 *                      ∓/± the variation — drives the area-range chart. Aligned
 *                      1:1 with {@link #progression}; low == high when the
 *                      variation is zero
 */
public record InflationResult(
        int totalMonths,
        BigDecimal inputAmount,
        BigDecimal resultAmount,
        boolean amountIsToday,
        List<InflationPoint> progression,
        List<InflationBand> band
) {
}
