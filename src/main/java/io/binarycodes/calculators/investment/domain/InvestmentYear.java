package io.binarycodes.calculators.investment.domain;

import java.math.BigDecimal;

/**
 * One year of the investment projection.
 *
 * @param yearsFromNow   0 for the first projected year
 * @param year           calendar year for this row's end-of-period
 * @param monthsInPeriod months this row aggregates (12 except possibly the last)
 * @param phase          which phase this year falls in
 * @param contribution   total contributed during this period (step-up applied)
 * @param balance        end-of-period nominal balance
 * @param principal      end-of-period principal (sum of contributions to date)
 * @param gains          {@code balance − principal}
 * @param realValue      {@link #balance} deflated to today's money at the inflation rate
 */
public record InvestmentYear(
        int yearsFromNow,
        int year,
        int monthsInPeriod,
        Phase phase,
        BigDecimal contribution,
        BigDecimal balance,
        BigDecimal principal,
        BigDecimal gains,
        BigDecimal realValue
) {
    /** Which phase a projection year belongs to. */
    public enum Phase {
        INVESTING,
        HOLDING
    }
}
