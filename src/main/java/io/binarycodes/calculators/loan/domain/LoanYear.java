package io.binarycodes.calculators.loan.domain;

import java.math.BigDecimal;

/**
 * One year of a loan's amortization schedule (12 months, except possibly the
 * last). Amounts are the totals across the months the row aggregates.
 *
 * @param yearsFromNow 0 for the first projected year
 * @param year         calendar year for this row's end-of-period
 * @param monthsInPeriod months this row aggregates
 * @param emiPaid      regular EMI paid during the period (excludes prepayments)
 * @param interestPaid interest portion paid during the period
 * @param principalPaid principal portion of the EMIs during the period
 * @param prepayment   extra prepayment applied during the period
 * @param endBalance   outstanding balance at the end of the period
 */
public record LoanYear(
        int yearsFromNow,
        int year,
        int monthsInPeriod,
        BigDecimal emiPaid,
        BigDecimal interestPaid,
        BigDecimal principalPaid,
        BigDecimal prepayment,
        BigDecimal endBalance
) {
}
