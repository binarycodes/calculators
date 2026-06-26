package io.binarycodes.calculators.retirement.service;

import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.retirement.domain.ProjectionRow;
import io.binarycodes.calculators.retirement.domain.RecurringExpense;
import io.binarycodes.calculators.retirement.domain.RecurringIncome;
import io.binarycodes.calculators.retirement.domain.RetirementBenefit;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;
import io.binarycodes.calculators.retirement.domain.RetirementResult;
import io.binarycodes.calculators.retirement.domain.TimelineEvent;
import io.binarycodes.calculators.retirement.domain.TimelineEventType;
import io.binarycodes.calculators.retirement.domain.TimelineYear;
import io.binarycodes.calculators.retirement.domain.WealthMilestones;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Derives the major-events timeline from a finished retirement projection: the
 * current state, retirement, lump-sum benefits, one-off and recurring
 * cashflows, nominal wealth milestones, the year drawdown begins, and depletion.
 * All events landing in the same year are clubbed under one {@link TimelineYear}.
 * Stateless static utility, mirroring {@code RetirementCalculator}.
 */
public final class RetirementTimeline {

    private RetirementTimeline() {
    }

    public static List<TimelineYear> build(RetirementInputs inputs,
                                           RetirementResult result,
                                           SupportedCurrency currency) {
        final List<ProjectionRow> rows = result.rows();
        if (rows.isEmpty()) {
            return List.of();
        }

        final int firstAge = rows.get(0).age();
        final int firstYear = rows.get(0).year();
        final int lastAge = rows.get(rows.size() - 1).age();

        // One row per age, so age ⇄ calendar-year is a simple linear shift.
        final int yearOffset = firstYear - firstAge;

        final Map<Integer, List<TimelineEvent>> byAge = new TreeMap<>();

        addEvent(byAge, firstAge, TimelineEvent.of(TimelineEventType.CURRENT_STATE, inputs.getCorpus()));

        final int retireAge = inputs.getRetireAge();
        addEvent(byAge, retireAge, TimelineEvent.of(TimelineEventType.RETIREMENT));
        for (final RetirementBenefit benefit : inputs.getRetirementBenefits()) {
            addEvent(byAge, retireAge,
                    new TimelineEvent(TimelineEventType.RETIREMENT_BENEFIT, benefit.getAmount(), benefit.getDescription()));
        }

        addMilestones(byAge, rows, currency);
        addDrawdownBegins(byAge, rows);
        result.corpusDepletedAt()
                .ifPresent(age -> addEvent(byAge, age, TimelineEvent.of(TimelineEventType.DEPLETION)));

        for (final var income : inputs.getFutureIncomes()) {
            addAtYear(byAge, income.getYear(), firstAge, lastAge, yearOffset,
                    new TimelineEvent(TimelineEventType.FUTURE_INCOME, income.getAmount(), income.getDescription()));
        }
        for (final var expense : inputs.getFutureExpenses()) {
            addAtYear(byAge, expense.getYear(), firstAge, lastAge, yearOffset,
                    new TimelineEvent(TimelineEventType.FUTURE_EXPENSE, expense.getAmount(), expense.getDescription()));
        }

        for (final RecurringIncome income : inputs.getRecurringIncomes()) {
            addAtYear(byAge, income.getYear(), firstAge, lastAge, yearOffset,
                    new TimelineEvent(TimelineEventType.RECURRING_INCOME_START, income.getAmount(), income.getDescription()));
            addAtYear(byAge, income.getStopYear(), firstAge, lastAge, yearOffset,
                    new TimelineEvent(TimelineEventType.RECURRING_INCOME_STOP, income.getAmount(), income.getDescription()));
        }
        for (final RecurringExpense expense : inputs.getRecurringExpenses()) {
            addAtYear(byAge, expense.getYear(), firstAge, lastAge, yearOffset,
                    new TimelineEvent(TimelineEventType.RECURRING_EXPENSE_START, expense.getAmount(), expense.getDescription()));
            addAtYear(byAge, expense.getStopYear(), firstAge, lastAge, yearOffset,
                    new TimelineEvent(TimelineEventType.RECURRING_EXPENSE_STOP, expense.getAmount(), expense.getDescription()));
        }

        final List<TimelineYear> timeline = new ArrayList<>();
        byAge.forEach((age, events) -> timeline.add(new TimelineYear(age, age + yearOffset, events)));
        return timeline;
    }

    private static void addMilestones(Map<Integer, List<TimelineEvent>> byAge,
                                      List<ProjectionRow> rows,
                                      SupportedCurrency currency) {
        final List<BigDecimal> thresholds = WealthMilestones.thresholdsFor(currency);
        int nextThreshold = 0;
        for (final ProjectionRow row : rows) {
            while (nextThreshold < thresholds.size()
                    && row.endCorpus().compareTo(thresholds.get(nextThreshold)) >= 0) {
                addEvent(byAge, row.age(),
                        TimelineEvent.of(TimelineEventType.MILESTONE, thresholds.get(nextThreshold)));
                nextThreshold++;
            }
        }
    }

    private static void addDrawdownBegins(Map<Integer, List<TimelineEvent>> byAge, List<ProjectionRow> rows) {
        for (final ProjectionRow row : rows) {
            if (row.endCorpus().compareTo(row.startCorpus()) < 0) {
                addEvent(byAge, row.age(), TimelineEvent.of(TimelineEventType.DRAWDOWN_BEGINS));
                return;
            }
        }
    }

    private static void addAtYear(Map<Integer, List<TimelineEvent>> byAge,
                                  Integer year, int firstAge, int lastAge, int yearOffset,
                                  TimelineEvent event) {
        if (year == null) {
            return;
        }
        final int age = year - yearOffset;
        if (age >= firstAge && age <= lastAge) {
            addEvent(byAge, age, event);
        }
    }

    private static void addEvent(Map<Integer, List<TimelineEvent>> byAge, int age, TimelineEvent event) {
        byAge.computeIfAbsent(age, key -> new ArrayList<>()).add(event);
    }
}
