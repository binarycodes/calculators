package io.binarycodes.calculators.goal.domain;

import java.math.BigDecimal;

/**
 * End-of-month corpus snapshot used by the goal-planner chart for short
 * horizons (under three years), where a year-grained chart would render only
 * one or two data points.
 *
 * @param monthsFromNow 0-based month index measured from today
 * @param label         display label for the x-axis (e.g. "Jan 27")
 * @param balance       end-of-month corpus balance summed across buckets
 * @param principal     end-of-month principal (seed corpus + contributions)
 * @param gains         {@code balance − principal}
 */
public record MonthSnapshot(
        int monthsFromNow,
        String label,
        BigDecimal balance,
        BigDecimal principal,
        BigDecimal gains
) {
}
