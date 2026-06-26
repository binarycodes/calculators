package io.binarycodes.calculators.retirement.domain;

import java.util.Comparator;
import java.util.List;

/**
 * All timeline events that fall in one projection year, clubbed into a single
 * marker. {@code dominantType} is the highest-severity event present and drives
 * the marker's styling.
 */
public record TimelineYear(int age, int year, List<TimelineEvent> events) {

    public TimelineEventType dominantType() {
        return this.events.stream()
                .map(TimelineEvent::type)
                .max(Comparator.comparingInt(TimelineEventType::severity))
                .orElseThrow();
    }

    public int size() {
        return this.events.size();
    }
}
