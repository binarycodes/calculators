package io.binarycodes.calculators.irr.domain;

/**
 * How often a recurring cashflow repeats. Each value carries the number of
 * months between successive payments so the calculator can expand a recurring
 * entry into individual dated cashflows.
 */
public enum CashflowFrequency {
    MONTHLY(1),
    QUARTERLY(3),
    HALF_YEARLY(6),
    YEARLY(12);

    private final int monthsPerPeriod;

    CashflowFrequency(int monthsPerPeriod) {
        this.monthsPerPeriod = monthsPerPeriod;
    }

    public int monthsPerPeriod() {
        return this.monthsPerPeriod;
    }
}
