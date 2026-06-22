package io.binarycodes.calculators.buyrent.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Aggregate output of {@code BuyRentCalculator.calculate(...)}.
 *
 * @param monthlyEmi                first-year monthly mortgage payment (0 if no loan)
 * @param initialMonthlyCostBuy     EMI + monthly property tax + maintenance in month 1
 * @param initialMonthlyCostRent    first month's rent
 * @param homeValueAtHorizon        home price at end of the analysis period
 * @param equityAtHorizon           buy-path net worth at horizon (after sell costs, net of mortgage)
 * @param rentPortfolioAtHorizon    rent-path net worth at horizon
 * @param breakEvenYear             first year where equity ≥ rentPortfolio; −1 if none within horizon
 * @param rows                      year-by-year projection
 */
public record BuyRentResult(
        BigDecimal monthlyEmi,
        BigDecimal initialMonthlyCostBuy,
        BigDecimal initialMonthlyCostRent,
        BigDecimal homeValueAtHorizon,
        BigDecimal equityAtHorizon,
        BigDecimal rentPortfolioAtHorizon,
        int breakEvenYear,
        List<BuyRentYear> rows
) {
}
