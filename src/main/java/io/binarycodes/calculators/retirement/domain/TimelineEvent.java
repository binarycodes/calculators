package io.binarycodes.calculators.retirement.domain;

import java.math.BigDecimal;

/**
 * A single thing that happens in a given year on the retirement timeline. Holds
 * only data — no user-facing strings beyond {@code detail}, which is the
 * description the user typed for a future/recurring cashflow (null for derived
 * events like milestones or depletion). {@code amount} is the milestone
 * threshold or the cashflow amount, or null when the event has no figure.
 */
public record TimelineEvent(TimelineEventType type, BigDecimal amount, String detail) {

    public static TimelineEvent of(TimelineEventType type) {
        return new TimelineEvent(type, null, null);
    }

    public static TimelineEvent of(TimelineEventType type, BigDecimal amount) {
        return new TimelineEvent(type, amount, null);
    }
}
