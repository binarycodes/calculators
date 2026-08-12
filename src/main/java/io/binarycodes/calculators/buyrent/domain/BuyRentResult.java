package io.binarycodes.calculators.buyrent.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Aggregate output of {@code BuyRentCalculator.calculate(...)}.
 *
 * @param monthlyEmi                first-year monthly mortgage payment (0 if no loan)
 * @param initialMonthlyCostBuy     EMI + monthly property tax + maintenance in month 1
 * @param initialMonthlyCostRent    first month's rent
 * @param homeValueAtHorizon            home price at end of the analysis period
 * @param equityAtHorizon               buy-path net worth at horizon, pre-tax
 * @param equityAtHorizonAfterTax       buy-path net worth after property capital-gains tax
 * @param rentPortfolioAtHorizon        rent-path net worth at horizon, pre-tax
 * @param rentPortfolioAtHorizonAfterTax rent-path net worth after investment capital-gains tax
 * @param breakEvenYear                 first year where after-tax equity ≥ after-tax portfolio; −1 if none
 * @param cashFlowCrossoverYear         first year rent ≥ monthly buy cost (owning is cheaper to hold); −1 if none
 * @param rows                          year-by-year projection
 */
public record BuyRentResult(
        BigDecimal monthlyEmi,
        BigDecimal initialMonthlyCostBuy,
        BigDecimal initialMonthlyCostRent,
        BigDecimal homeValueAtHorizon,
        BigDecimal equityAtHorizon,
        BigDecimal equityAtHorizonAfterTax,
        BigDecimal rentPortfolioAtHorizon,
        BigDecimal rentPortfolioAtHorizonAfterTax,
        int breakEvenYear,
        int cashFlowCrossoverYear,
        List<BuyRentYear> rows
) {
}
