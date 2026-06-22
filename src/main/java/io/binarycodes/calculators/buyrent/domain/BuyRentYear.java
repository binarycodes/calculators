package io.binarycodes.calculators.buyrent.domain;

import java.math.BigDecimal;

/**
 * Snapshot of both paths at the end of one year in the buy-vs-rent projection.
 *
 * @param year                  year number (1 = end of first year)
 * @param homeValue             home price at end of this year
 * @param mortgageBalance       outstanding loan balance (0 after payoff)
 * @param equity                home value × (1 − sell cost %) − mortgage balance
 * @param rentPortfolio         accumulated investment portfolio (rent path)
 * @param cumulativeRentPaid    total rent paid through this year
 * @param cumulativeBuyCost     total buy-path costs (EMI + tax + maintenance) through this year
 * @param netDifference         equity − rentPortfolio; positive means buy is ahead
 * @param realNetDifference     netDifference deflated to today's money at the inflation rate
 */
public record BuyRentYear(
        int year,
        BigDecimal homeValue,
        BigDecimal mortgageBalance,
        BigDecimal equity,
        BigDecimal rentPortfolio,
        BigDecimal cumulativeRentPaid,
        BigDecimal cumulativeBuyCost,
        BigDecimal netDifference,
        BigDecimal realNetDifference
) {
}
