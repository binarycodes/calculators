package io.binarycodes.calculators.debt.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * One year of a payoff schedule.
 *
 * @param year               year number (1 = end of first year)
 * @param calendarYear       the actual calendar year this row ends in
 * @param totalBalance       total outstanding across all debts at year end
 * @param interestPaid       interest accrued during the year
 * @param principalPaid      principal cleared during the year
 * @param targets            debts that received surplus (beyond their minimum)
 *                           during the year, in strategy order
 * @param cumulativeInterest running total of interest through this year
 */
public record DebtPlanYear(
        int year,
        int calendarYear,
        BigDecimal totalBalance,
        BigDecimal interestPaid,
        BigDecimal principalPaid,
        List<String> targets,
        BigDecimal cumulativeInterest
) {
}
