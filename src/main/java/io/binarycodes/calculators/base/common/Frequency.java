package io.binarycodes.calculators.base.common;

/**
 * How often a recurring amount repeats. Each value carries the number of
 * months between successive occurrences, so a calculator can place occurrences
 * on a monthly timeline (occurrence every {@code monthsPerPeriod} months) or
 * annualise a per-period amount ({@code amount × periodsPerYear}). Shared by
 * every calculator that offers a contribution / prepayment / cashflow
 * frequency selector.
 */
public enum Frequency {
    MONTHLY(1),
    QUARTERLY(3),
    HALF_YEARLY(6),
    YEARLY(12);

    private final int monthsPerPeriod;

    Frequency(int monthsPerPeriod) {
        this.monthsPerPeriod = monthsPerPeriod;
    }

    public int monthsPerPeriod() {
        return this.monthsPerPeriod;
    }

    public int periodsPerYear() {
        return 12 / this.monthsPerPeriod;
    }
}
