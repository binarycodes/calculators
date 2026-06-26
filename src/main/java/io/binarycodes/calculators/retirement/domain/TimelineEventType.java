package io.binarycodes.calculators.retirement.domain;

/**
 * The kinds of event marked on the retirement timeline. {@code severity} ranks
 * the types so that, when several events fall in the same year and are clubbed
 * into one marker, the marker takes the styling of the most significant type
 * present (depletion outranks drawdown outranks retirement, the rest are
 * ordinary). It is an ordering weight only, not a domain value.
 */
public enum TimelineEventType {

    DEPLETION(100),
    DRAWDOWN_BEGINS(90),
    RETIREMENT(80),
    RETIREMENT_BENEFIT(70),
    CURRENT_STATE(60),
    MILESTONE(50),
    FUTURE_INCOME(40),
    FUTURE_EXPENSE(40),
    RECURRING_INCOME_START(30),
    RECURRING_INCOME_STOP(30),
    RECURRING_EXPENSE_START(30),
    RECURRING_EXPENSE_STOP(30);

    private final int severity;

    TimelineEventType(int severity) {
        this.severity = severity;
    }

    public int severity() {
        return this.severity;
    }
}
