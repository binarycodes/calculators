package io.binarycodes.calculators.debt.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * One strategy's simulated outcome.
 *
 * @param strategy          the strategy this schedule was run under, or null for
 *                          the minimums-only baseline
 * @param years             year-by-year schedule
 * @param monthlyPayments   month-by-month per-debt payment schedule
 * @param payoffMonth       month index (1-based) the last debt cleared, or the
 *                          cap when the debts never clear
 * @param fullyPaid         true when every debt cleared within the cap
 * @param totalInterest     total interest paid over the run
 * @param totalPaid         total cash paid (principal + interest + any fees)
 * @param realTotalInterest total interest in today's money (inflation-deflated)
 * @param payoffMonthByDebt month each debt cleared, keyed by debt name
 */
public record DebtScheduleResult(
        PayoffStrategy strategy,
        List<DebtPlanYear> years,
        List<MonthlyPayment> monthlyPayments,
        int payoffMonth,
        boolean fullyPaid,
        BigDecimal totalInterest,
        BigDecimal totalPaid,
        BigDecimal realTotalInterest,
        Map<String, Integer> payoffMonthByDebt
) {
}
