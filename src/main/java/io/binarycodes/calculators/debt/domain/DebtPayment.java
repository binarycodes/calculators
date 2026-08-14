package io.binarycodes.calculators.debt.domain;

import java.math.BigDecimal;

/**
 * What a single debt receives in one month of the schedule.
 *
 * @param debtName  the debt this payment goes to
 * @param amount    the amount paid into it this month
 * @param defaulted true when the budget could not cover this debt's required
 *                  minimum that month while it still owed a balance
 */
public record DebtPayment(String debtName, BigDecimal amount, boolean defaulted) {
}
