package io.binarycodes.calculators.debt.domain;

import java.math.BigDecimal;

/**
 * What a single debt receives in one month of the schedule.
 *
 * @param debtName  the debt this payment goes to
 * @param amount    the amount paid into it this month
 * @param shortfall how far the payment fell below the required minimum when the
 *                  budget couldn't cover it (zero when there is no shortfall)
 * @param defaulted true when {@link #shortfall} is positive — the debt still owed
 *                  a balance but didn't get its full minimum
 */
public record DebtPayment(String debtName, BigDecimal amount, BigDecimal shortfall, boolean defaulted) {
}
