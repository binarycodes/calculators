package io.binarycodes.calculators.debt.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * One month of the payment schedule: how much goes into each debt, and the
 * total paid that month. {@link #payments} is aligned to the plan's debt order.
 *
 * @param month    month index, 1-based from the plan start
 * @param payments per-debt payments this month
 * @param total    sum of all payments this month
 */
public record MonthlyPayment(int month, List<DebtPayment> payments, BigDecimal total) {

    /** True when any debt defaulted this month. */
    public boolean hasDefault() {
        return payments.stream().anyMatch(DebtPayment::defaulted);
    }

    /** The combined shortfall across all debts this month (zero when none defaulted). */
    public BigDecimal totalShortfall() {
        return payments.stream().map(DebtPayment::shortfall).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
