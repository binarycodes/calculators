package io.binarycodes.calculators.inflation.domain;

import java.math.BigDecimal;

/**
 * One point on the inflation progression curve — the nominal value at a given
 * year, growing from today's value at the inflation rate. Used by the chart to
 * visualise how the amount drifts over the horizon.
 *
 * @param year  calendar year for this point
 * @param value nominal amount at that year
 */
public record InflationPoint(int year, BigDecimal value) {
}
