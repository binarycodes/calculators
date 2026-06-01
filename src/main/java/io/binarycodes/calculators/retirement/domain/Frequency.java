package io.binarycodes.calculators.retirement.domain;

/**
 * How often a recurring expense or income repeats. {@code MONTHLY} amounts
 * are interpreted as the per-month value; {@code YEARLY} amounts are the
 * per-year value. The calculator annualises both when folding them into a
 * projection year.
 */
public enum Frequency {
    MONTHLY("Monthly"),
    YEARLY("Yearly");

    private final String displayName;

    Frequency(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return this.displayName;
    }
}
