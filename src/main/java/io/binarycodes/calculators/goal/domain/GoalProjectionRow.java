package io.binarycodes.calculators.goal.domain;

import java.math.BigDecimal;

/**
 * One year of the goal-planner projection.
 *
 * @param yearsFromNow      0 for the first projected year
 * @param year              calendar year for this row's end-of-period
 * @param age               investor's age at the end of this period, or {@code null}
 *                          when the horizon mode doesn't carry an age
 * @param monthsInPeriod    months this row aggregates (12 for a full year,
 *                          {@code 1..11} only for the final row of a non-whole-year horizon)
 * @param yearlyContribution gross SIP contribution made in this period (step-up applied)
 * @param balance           end-of-period corpus balance (sum across buckets)
 * @param principal         end-of-period principal (seed corpus + contributions to date)
 * @param gains             {@code balance − principal}
 * @param taxIfWithdrawn    tax that would be due if the entire corpus were
 *                          withdrawn at this period's end — each bucket's
 *                          gains taxed at its own rate
 */
public record GoalProjectionRow(
        int yearsFromNow,
        int year,
        Integer age,
        int monthsInPeriod,
        BigDecimal yearlyContribution,
        BigDecimal balance,
        BigDecimal principal,
        BigDecimal gains,
        BigDecimal taxIfWithdrawn
) {
}
