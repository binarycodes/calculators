package io.binarycodes.calculators.inflation.domain;

import java.math.BigDecimal;

/**
 * One point of the inflation uncertainty band — the lower and upper value at a
 * given year across the low and high inflation rates (central rate ∓/± the
 * variation). Drives the area-range chart. When the variation is zero,
 * {@code low} equals {@code high}. In forward mode the lower edge comes from the
 * low rate; in backward (discounting) mode it comes from the high rate, which
 * discounts the fixed future amount to a smaller present value.
 *
 * @param year calendar year for this point
 * @param low  the smaller of the two trajectory values at this year
 * @param high the larger of the two trajectory values at this year
 */
public record InflationBand(int year, BigDecimal low, BigDecimal high) {
}
