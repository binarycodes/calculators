package io.binarycodes.calculators.buyrent.domain;

import java.math.BigDecimal;

/**
 * Snapshot of both paths at the end of one year in the buy-vs-rent projection.
 *
 * @param year                  year number (1 = end of first year)
 * @param homeValue             home price at end of this year
 * @param mortgageBalance       outstanding loan balance (0 after payoff)
 * @param equity                home value × (1 − sell cost %) − mortgage balance (pre-tax)
 * @param equityAfterTax        equity after property capital-gains tax on the profit
 * @param rentPortfolio         accumulated investment portfolio, pre-tax
 * @param rentPortfolioAfterTax portfolio after capital-gains tax on investment gains
 * @param cumulativeRentPaid    total rent paid through this year
 * @param cumulativeBuyCost     total cash paid on the buy path through this year — down payment + buying costs + EMI + tax + maintenance
 * @param netDifference         equityAfterTax − rentPortfolioAfterTax; positive means buy is ahead
 * @param realNetDifference     netDifference deflated to today's money at the inflation rate
 */
public record BuyRentYear(
        int year,
        BigDecimal homeValue,
        BigDecimal mortgageBalance,
        BigDecimal equity,
        BigDecimal equityAfterTax,
        BigDecimal rentPortfolio,
        BigDecimal rentPortfolioAfterTax,
        BigDecimal cumulativeRentPaid,
        BigDecimal cumulativeBuyCost,
        BigDecimal netDifference,
        BigDecimal realNetDifference
) {
}
