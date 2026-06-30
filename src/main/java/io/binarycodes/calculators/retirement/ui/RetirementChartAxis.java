package io.binarycodes.calculators.retirement.ui;

/**
 * Shared x-axis convention for the retirement value charts. Each projection row
 * carries the value for the year during which the person is its {@code age}, so
 * every value chart plots it against the age reached at that year's end
 * ({@code age + 1}). This keeps the corpus balance, expenses, returns, and
 * withdrawals for the same year aligned across all the chart tabs.
 *
 * <p>The event {@link TimelineChart} deliberately does not use this: it marks
 * events at the age they actually occur, not a year-end.</p>
 */
final class RetirementChartAxis {

    private RetirementChartAxis() {
    }

    /** Age at the end of the year the row describes. */
    static int yearEndAge(int rowAge) {
        return rowAge + 1;
    }
}
