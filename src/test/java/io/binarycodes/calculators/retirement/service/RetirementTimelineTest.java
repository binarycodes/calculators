package io.binarycodes.calculators.retirement.service;

import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.base.common.Frequency;
import io.binarycodes.calculators.retirement.domain.FutureIncome;
import io.binarycodes.calculators.retirement.domain.ProjectionRow;
import io.binarycodes.calculators.retirement.domain.RecurringExpense;
import io.binarycodes.calculators.retirement.domain.RecurringIncome;
import io.binarycodes.calculators.retirement.domain.RetirementBenefit;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;
import io.binarycodes.calculators.retirement.domain.RetirementResult;
import io.binarycodes.calculators.retirement.domain.TimelineEvent;
import io.binarycodes.calculators.retirement.domain.TimelineEventType;
import io.binarycodes.calculators.retirement.domain.TimelineYear;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetirementTimelineTest {

    private static final int BASE_AGE = 40;
    private static final int BASE_YEAR = 2026;

    @Test
    void milestones_differ_by_currency_and_keep_only_the_highest_per_year() {
        final List<ProjectionRow> rows = List.of(
                row(40, 1_500_000, 2_000_000),
                row(41, 5_500_000, 6_000_000),
                row(42, 55_000_000, 60_000_000));
        final RetirementInputs inputs = inputs(40, 40);
        final RetirementResult result = result(rows, Optional.empty());

        final List<TimelineYear> inr = RetirementTimeline.build(inputs, result, SupportedCurrency.INR);
        // INR thresholds include ₹50L (5e6), so age 41 earns a milestone.
        assertTrue(amounts(inr, 41, TimelineEventType.MILESTONE).contains(bd(5_000_000)),
                "INR should hit the ₹50L milestone at 41");
        // ₹1cr (1e7) and ₹5cr (5e7) both cleared at 42 → keep only the highest.
        assertEquals(List.of(bd(50_000_000)),
                amounts(inr, 42, TimelineEventType.MILESTONE));

        final List<TimelineYear> usd = RetirementTimeline.build(inputs, result, SupportedCurrency.USD);
        // USD set jumps 1M → 10M, so 6M earns nothing at 41.
        assertTrue(types(usd, 41).stream().noneMatch(t -> t == TimelineEventType.MILESTONE),
                "USD should have no milestone at 41");
        assertEquals(List.of(bd(50_000_000)),
                amounts(usd, 42, TimelineEventType.MILESTONE));
    }

    @Test
    void drawdown_begins_marks_only_the_first_shrinking_year() {
        final List<ProjectionRow> rows = List.of(
                row(40, 1, 2),
                row(41, 10, 8),
                row(42, 8, 6));
        final List<TimelineYear> timeline =
                RetirementTimeline.build(inputs(40, 40), result(rows, Optional.empty()), SupportedCurrency.USD);

        assertTrue(types(timeline, 41).contains(TimelineEventType.DRAWDOWN_BEGINS));
        assertFalse(types(timeline, 42).contains(TimelineEventType.DRAWDOWN_BEGINS),
                "drawdown marker fires once, at the first shrinking year");
    }

    @Test
    void depletion_is_taken_from_the_result() {
        final List<ProjectionRow> rows = List.of(row(40, 5, 5), row(41, 5, 5), row(42, 5, 5));

        final List<TimelineYear> depleted =
                RetirementTimeline.build(inputs(40, 40), result(rows, Optional.of(42)), SupportedCurrency.USD);
        assertTrue(types(depleted, 42).contains(TimelineEventType.DEPLETION));

        final List<TimelineYear> survives =
                RetirementTimeline.build(inputs(40, 40), result(rows, Optional.empty()), SupportedCurrency.USD);
        assertTrue(survives.stream().flatMap(y -> y.events().stream())
                        .noneMatch(e -> e.type() == TimelineEventType.DEPLETION),
                "no depletion event when the corpus survives");
    }

    @Test
    void future_and_recurring_events_map_to_the_right_age() {
        final List<ProjectionRow> rows = List.of(
                row(40, 5, 5), row(41, 5, 5), row(42, 5, 5), row(43, 5, 5), row(44, 5, 5));
        final RetirementInputs inputs = inputs(40, 40);
        inputs.getFutureIncomes().add(futureIncome(BASE_YEAR + 2, "Inheritance"));
        inputs.getRecurringExpenses().add(recurringExpense(BASE_YEAR + 1, BASE_YEAR + 3, "Rent"));
        // A recurring income with no stop year contributes only a START.
        inputs.getRecurringIncomes().add(recurringIncome(BASE_YEAR + 1, null, "Rental"));

        final List<TimelineYear> timeline =
                RetirementTimeline.build(inputs, result(rows, Optional.empty()), SupportedCurrency.USD);

        assertTrue(types(timeline, 42).contains(TimelineEventType.FUTURE_INCOME));
        assertTrue(types(timeline, 41).contains(TimelineEventType.RECURRING_EXPENSE_START));
        assertTrue(types(timeline, 43).contains(TimelineEventType.RECURRING_EXPENSE_STOP));
        assertTrue(types(timeline, 41).contains(TimelineEventType.RECURRING_INCOME_START));
        assertTrue(timeline.stream().flatMap(y -> y.events().stream())
                        .noneMatch(e -> e.type() == TimelineEventType.RECURRING_INCOME_STOP),
                "open-ended recurring income has no stop marker");
    }

    @Test
    void current_state_and_retirement_are_clubbed_and_years_sorted() {
        final List<ProjectionRow> rows = List.of(row(40, 5, 5), row(41, 5, 5), row(42, 5, 5));
        final RetirementInputs inputs = inputs(40, 41);
        inputs.getRetirementBenefits().add(new RetirementBenefit("Pension", bd(1_000_000), bd(0)));

        final List<TimelineYear> timeline =
                RetirementTimeline.build(inputs, result(rows, Optional.empty()), SupportedCurrency.USD);

        assertTrue(types(timeline, 40).contains(TimelineEventType.CURRENT_STATE));
        assertTrue(types(timeline, 41).contains(TimelineEventType.RETIREMENT));
        assertTrue(types(timeline, 41).contains(TimelineEventType.RETIREMENT_BENEFIT));
        assertEquals(TimelineEventType.RETIREMENT, year(timeline, 41).dominantType());

        final List<Integer> ages = timeline.stream().map(TimelineYear::age).toList();
        assertEquals(ages.stream().sorted().toList(), ages, "years are sorted by age");
    }

    private static List<TimelineEventType> types(List<TimelineYear> timeline, int age) {
        return eventsAt(timeline, age).stream().map(TimelineEvent::type).toList();
    }

    private static List<BigDecimal> amounts(List<TimelineYear> timeline, int age, TimelineEventType type) {
        return eventsAt(timeline, age).stream()
                .filter(e -> e.type() == type)
                .map(TimelineEvent::amount)
                .toList();
    }

    private static List<TimelineEvent> eventsAt(List<TimelineYear> timeline, int age) {
        return timeline.stream().filter(y -> y.age() == age)
                .findFirst().map(TimelineYear::events).orElse(List.of());
    }

    private static TimelineYear year(List<TimelineYear> timeline, int age) {
        return timeline.stream().filter(y -> y.age() == age).findFirst()
                .orElseThrow(() -> new AssertionError("no timeline year for age " + age));
    }

    private static ProjectionRow row(int age, long startCorpus, long endCorpus) {
        return new ProjectionRow(BASE_YEAR + (age - BASE_AGE), age, age == BASE_AGE, false,
                BigDecimal.ZERO, bd(startCorpus), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, bd(endCorpus), false);
    }

    private static RetirementInputs inputs(int currentAge, int retireAge) {
        final var inputs = new RetirementInputs();
        inputs.setCurrentAge(currentAge);
        inputs.setRetireAge(retireAge);
        inputs.setLifeExp(90);
        inputs.setCorpus(bd(1_000_000));
        inputs.setFutureExpenses(new ArrayList<>());
        inputs.setFutureIncomes(new ArrayList<>());
        inputs.setRetirementBenefits(new ArrayList<>());
        inputs.setRecurringExpenses(new ArrayList<>());
        inputs.setRecurringIncomes(new ArrayList<>());
        return inputs;
    }

    private static RetirementResult result(List<ProjectionRow> rows, Optional<Integer> depletedAt) {
        return new RetirementResult(rows, depletedAt, BigDecimal.ZERO);
    }

    private static FutureIncome futureIncome(int year, String description) {
        return new FutureIncome(year, description, bd(2_000_000), bd(0));
    }

    private static RecurringExpense recurringExpense(int year, Integer stopYear, String description) {
        return new RecurringExpense(year, stopYear, description, Frequency.YEARLY, bd(120_000), bd(0));
    }

    private static RecurringIncome recurringIncome(int year, Integer stopYear, String description) {
        return new RecurringIncome(year, stopYear, description, Frequency.YEARLY, bd(50_000), bd(0));
    }

    private static BigDecimal bd(long value) {
        return BigDecimal.valueOf(value);
    }
}
