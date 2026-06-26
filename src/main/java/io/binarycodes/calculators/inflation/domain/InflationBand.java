package io.binarycodes.calculators.inflation.domain;

import java.math.BigDecimal;

/**
 * One point of the inflation uncertainty band — the value at a given year under
 * the low and high inflation rates (central rate ∓/± the variation). Drives the
 * area-range chart. When the variation is zero, {@code low} equals {@code high}.
 *
 * @param year calendar year for this point
 * @param low  value at the lower inflation rate (rate − variation)
 * @param high value at the higher inflation rate (rate + variation)
 */
public record InflationBand(int year, BigDecimal low, BigDecimal high) {
}
