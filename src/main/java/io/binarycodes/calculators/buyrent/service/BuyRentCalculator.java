package io.binarycodes.calculators.buyrent.service;

import io.binarycodes.calculators.base.math.Rates;
import io.binarycodes.calculators.buyrent.domain.BuyRentInputs;
import io.binarycodes.calculators.buyrent.domain.BuyRentResult;
import io.binarycodes.calculators.buyrent.domain.BuyRentYear;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Stateless buy-vs-rent projection engine. Runs a month-by-month simulation of
 * both paths over the analysis horizon and snapshots net-worth at each year
 * boundary.
 *
 * <p><b>Buy path</b> — down payment + buying costs paid upfront; monthly EMI
 * until loan payoff; annual property tax and maintenance as a % of current home
 * value; home appreciates at the stated rate. On exit: property capital-gains
 * tax applies to the profit (sale proceeds − cost basis).</p>
 *
 * <p><b>Rent path</b> — the down payment + buying costs that were not spent are
 * invested immediately at the investment return rate; each month any surplus
 * (buy costs > rent) is added to the portfolio and any deficit (rent > buy
 * costs) is withdrawn; rent grows annually. On exit: investment capital-gains
 * tax applies to the gain (portfolio − net contributions).</p>
 *
 * <p>Break-even is the first year where after-tax buy equity ≥ after-tax rent
 * portfolio.</p>
 */
public final class BuyRentCalculator {

    private BuyRentCalculator() {
    }

    public static BuyRentResult calculate(BuyRentInputs inputs) {
        validateInputs(inputs);

        final BigDecimal homePrice = inputs.getHomePrice();
        final BigDecimal downFraction = Rates.pctToFraction(inputs.getDownPaymentPct());
        final BigDecimal buyingCostFraction = Rates.pctToFraction(inputs.getBuyingCostPct());
        final BigDecimal sellingCostFraction = Rates.pctToFraction(inputs.getSellingCostPct());
        final BigDecimal propertyTaxFraction = Rates.pctToFraction(inputs.getPropertyTaxRatePct());
        final BigDecimal maintenanceFraction = Rates.pctToFraction(inputs.getMaintenancePct());
        final BigDecimal appreciationFraction = Rates.pctToFraction(inputs.getAppreciationPct());
        final BigDecimal rentIncreaseFraction = Rates.pctToFraction(inputs.getRentIncreasePct());
        final BigDecimal propertyCgtFraction = Rates.pctToFraction(inputs.getPropertyCapitalGainsTaxPct());
        final BigDecimal investmentCgtFraction = Rates.pctToFraction(inputs.getInvestmentGainsTaxPct());

        // Cost basis for property capital-gains calculation (home price + buying costs).
        final BigDecimal propertyCostBasis = homePrice.multiply(
                BigDecimal.ONE.add(buyingCostFraction, Rates.CONTEXT), Rates.CONTEXT);

        final BigDecimal loanAmount = homePrice.multiply(
                BigDecimal.ONE.subtract(downFraction, Rates.CONTEXT), Rates.CONTEXT);
        final int loanMonths = (inputs.getLoanTermYears() == null ? 0 : inputs.getLoanTermYears()) * 12;

        final BigDecimal monthlyMortgageRate = Rates.monthlyFromAnnual(
                Rates.pctToFraction(inputs.getMortgageRatePct()));
        final BigDecimal monthlyEmi = computeEmi(loanAmount, monthlyMortgageRate, loanMonths);

        final BigDecimal monthlyAppreciationRate = Rates.monthlyFromAnnual(appreciationFraction);
        final BigDecimal monthlyInvestmentRate = Rates.monthlyFromAnnual(
                Rates.pctToFraction(inputs.getInvestmentReturnPct()));
        final BigDecimal monthlyInflationRate = Rates.monthlyFromAnnual(
                Rates.pctToFraction(inputs.getInflationRatePct()));

        // Rent portfolio is seeded with the capital not spent on the purchase.
        final BigDecimal initialInvestment = homePrice.multiply(downFraction, Rates.CONTEXT)
                .add(homePrice.multiply(buyingCostFraction, Rates.CONTEXT), Rates.CONTEXT);

        BigDecimal homeValue = homePrice;
        BigDecimal mortgageBalance = loanAmount;
        BigDecimal rentPortfolio = initialInvestment;
        // Net contributions tracks the cost basis for the rent portfolio tax calculation.
        // Starts at the initial investment; monthly surpluses (positive or negative) are added.
        BigDecimal netContributions = initialInvestment;
        BigDecimal cumulativeRentPaid = BigDecimal.ZERO;
        // Total cash paid on the buy path, seeded with the upfront outlay (down
        // payment + buying costs); the monthly EMI + tax + maintenance accrue on top.
        BigDecimal cumulativeBuyCost = initialInvestment;
        BigDecimal inflationAccumulator = BigDecimal.ONE;

        final BigDecimal initialMonthlyRent = inputs.getMonthlyRent();
        final BigDecimal initialMonthlyCostBuy = computeInitialMonthlyCostBuy(
                monthlyEmi, homePrice, propertyTaxFraction, maintenanceFraction);

        final int totalMonths = (inputs.getAnalysisYears() == null ? 0 : inputs.getAnalysisYears()) * 12;
        final List<BuyRentYear> rows = new ArrayList<>(inputs.getAnalysisYears() == null ? 0 : inputs.getAnalysisYears());
        int breakEvenYear = -1;
        int cashFlowCrossoverYear = -1;

        for (int month = 1; month <= totalMonths; month++) {
            homeValue = homeValue.multiply(BigDecimal.ONE.add(monthlyAppreciationRate, Rates.CONTEXT), Rates.CONTEXT);
            inflationAccumulator = inflationAccumulator.multiply(
                    BigDecimal.ONE.add(monthlyInflationRate, Rates.CONTEXT), Rates.CONTEXT);

            // Buy path — amortize one month.
            BigDecimal emiThisMonth = BigDecimal.ZERO;
            if (month <= loanMonths && monthlyMortgageRate.signum() > 0) {
                final BigDecimal interestPortion = mortgageBalance.multiply(monthlyMortgageRate, Rates.CONTEXT);
                final BigDecimal principalPortion = monthlyEmi.subtract(interestPortion, Rates.CONTEXT);
                mortgageBalance = mortgageBalance.subtract(principalPortion, Rates.CONTEXT);
                if (mortgageBalance.signum() < 0) {
                    mortgageBalance = BigDecimal.ZERO;
                }
                emiThisMonth = monthlyEmi;
            } else if (month <= loanMonths && monthlyMortgageRate.signum() == 0) {
                final BigDecimal principalPortion = loanAmount.divide(BigDecimal.valueOf(loanMonths), Rates.CONTEXT);
                mortgageBalance = mortgageBalance.subtract(principalPortion, Rates.CONTEXT);
                if (mortgageBalance.signum() < 0) {
                    mortgageBalance = BigDecimal.ZERO;
                }
                emiThisMonth = principalPortion;
            }

            final BigDecimal monthlyTaxAndMaint = homeValue
                    .multiply(propertyTaxFraction.add(maintenanceFraction, Rates.CONTEXT), Rates.CONTEXT)
                    .divide(BigDecimal.valueOf(12), Rates.CONTEXT);
            final BigDecimal totalBuyCostThisMonth = emiThisMonth.add(monthlyTaxAndMaint, Rates.CONTEXT);
            cumulativeBuyCost = cumulativeBuyCost.add(totalBuyCostThisMonth, Rates.CONTEXT);

            // Rent path — grow portfolio, invest/withdraw surplus. Rent steps once
            // a year (flat within the year), so the increase applies per completed
            // year rather than compounding every month.
            final int completedRentYears = (month - 1) / 12;
            final BigDecimal monthlyRentNow = initialMonthlyRent.multiply(
                    Rates.pow1plus(rentIncreaseFraction, completedRentYears), Rates.CONTEXT);
            cumulativeRentPaid = cumulativeRentPaid.add(monthlyRentNow, Rates.CONTEXT);

            rentPortfolio = rentPortfolio.multiply(
                    BigDecimal.ONE.add(monthlyInvestmentRate, Rates.CONTEXT), Rates.CONTEXT);
            // The renter invests only what buying would have cost above the rent.
            // Once rent overtakes the buy cost there is nothing left to invest —
            // contributions stop, but the existing corpus keeps compounding. We
            // don't draw the portfolio down: covering the higher rent from income
            // is an affordability question, separate from the buy-vs-rent tally.
            final BigDecimal surplus = totalBuyCostThisMonth.subtract(monthlyRentNow, Rates.CONTEXT).max(BigDecimal.ZERO);
            rentPortfolio = rentPortfolio.add(surplus, Rates.CONTEXT);
            netContributions = netContributions.add(surplus, Rates.CONTEXT);

            // First month owning is cheaper to hold than renting — the cash-flow
            // crossover, usually around loan payoff. Recorded once.
            if (cashFlowCrossoverYear < 0 && monthlyRentNow.compareTo(totalBuyCostThisMonth) >= 0) {
                cashFlowCrossoverYear = (month - 1) / 12 + 1;
            }

            // Snapshot at each year boundary.
            if (month % 12 == 0) {
                final int year = month / 12;
                final BigDecimal currentMortgageBalance = mortgageBalance.max(BigDecimal.ZERO);
                final BigDecimal saleProceeds = homeValue
                        .multiply(BigDecimal.ONE.subtract(sellingCostFraction, Rates.CONTEXT), Rates.CONTEXT);
                final BigDecimal equity = saleProceeds.subtract(currentMortgageBalance, Rates.CONTEXT);

                // Property capital-gains tax: tax on profit above cost basis.
                final BigDecimal propertyGain = saleProceeds.subtract(propertyCostBasis, Rates.CONTEXT);
                final BigDecimal propertyCgt = propertyGain.signum() > 0
                        ? propertyGain.multiply(propertyCgtFraction, Rates.CONTEXT)
                        : BigDecimal.ZERO;
                final BigDecimal equityAfterTax = equity.subtract(propertyCgt, Rates.CONTEXT);

                // Investment capital-gains tax: tax on gain above net contributions.
                final BigDecimal investmentGain = rentPortfolio.subtract(netContributions, Rates.CONTEXT);
                final BigDecimal investmentCgt = investmentGain.signum() > 0
                        ? investmentGain.multiply(investmentCgtFraction, Rates.CONTEXT)
                        : BigDecimal.ZERO;
                final BigDecimal rentPortfolioAfterTax = rentPortfolio.subtract(investmentCgt, Rates.CONTEXT);

                final BigDecimal netDiff = equityAfterTax.subtract(rentPortfolioAfterTax, Rates.CONTEXT);

                rows.add(new BuyRentYear(
                        year,
                        homeValue,
                        currentMortgageBalance,
                        equity,
                        equityAfterTax,
                        rentPortfolio,
                        rentPortfolioAfterTax,
                        cumulativeRentPaid,
                        cumulativeBuyCost,
                        netDiff));

                if (breakEvenYear < 0 && equityAfterTax.compareTo(rentPortfolioAfterTax) >= 0) {
                    breakEvenYear = year;
                }
            }
        }

        final BuyRentYear lastRow = rows.isEmpty() ? null : rows.get(rows.size() - 1);
        // Deflate the horizon net worths to present value; inflationAccumulator
        // now holds (1 + monthly inflation) compounded over the whole horizon.
        final BigDecimal equityAtHorizonAfterTax = lastRow == null ? BigDecimal.ZERO : lastRow.equityAfterTax();
        final BigDecimal rentPortfolioAtHorizonAfterTax = lastRow == null ? initialInvestment : lastRow.rentPortfolioAfterTax();
        return new BuyRentResult(
                monthlyEmi,
                initialMonthlyCostBuy,
                initialMonthlyRent,
                lastRow == null ? homePrice : lastRow.homeValue(),
                lastRow == null ? BigDecimal.ZERO : lastRow.equity(),
                equityAtHorizonAfterTax,
                lastRow == null ? initialInvestment : lastRow.rentPortfolio(),
                rentPortfolioAtHorizonAfterTax,
                equityAtHorizonAfterTax.divide(inflationAccumulator, Rates.CONTEXT),
                rentPortfolioAtHorizonAfterTax.divide(inflationAccumulator, Rates.CONTEXT),
                breakEvenYear,
                cashFlowCrossoverYear,
                rows);
    }

    private static BigDecimal computeEmi(BigDecimal principal, BigDecimal monthlyRate, int months) {
        if (months <= 0 || principal.signum() == 0) {
            return BigDecimal.ZERO;
        }
        if (monthlyRate.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(months), Rates.CONTEXT);
        }
        final BigDecimal factor = Rates.pow1plus(monthlyRate, months);
        return principal.multiply(monthlyRate, Rates.CONTEXT)
                .multiply(factor, Rates.CONTEXT)
                .divide(factor.subtract(BigDecimal.ONE, Rates.CONTEXT), Rates.CONTEXT);
    }

    private static BigDecimal computeInitialMonthlyCostBuy(BigDecimal monthlyEmi, BigDecimal homePrice,
                                                           BigDecimal propertyTaxFraction,
                                                           BigDecimal maintenanceFraction) {
        final BigDecimal monthlyTaxAndMaint = homePrice
                .multiply(propertyTaxFraction.add(maintenanceFraction, MathContext.DECIMAL64), MathContext.DECIMAL64)
                .divide(BigDecimal.valueOf(12), MathContext.DECIMAL64);
        return monthlyEmi.add(monthlyTaxAndMaint, MathContext.DECIMAL64);
    }

    private static void validateInputs(BuyRentInputs inputs) {
        if (inputs.getHomePrice() == null || inputs.getHomePrice().signum() <= 0) {
            throw new IllegalArgumentException("Home price must be positive");
        }
        if (inputs.getMonthlyRent() == null || inputs.getMonthlyRent().signum() <= 0) {
            throw new IllegalArgumentException("Monthly rent must be positive");
        }
        if (inputs.getAnalysisYears() == null || inputs.getAnalysisYears() < 1) {
            throw new IllegalArgumentException("Analysis horizon must be at least 1 year");
        }
        if (inputs.getDownPaymentPct() != null
                && inputs.getDownPaymentPct().compareTo(BigDecimal.valueOf(100)) >= 0) {
            throw new IllegalArgumentException("Down payment must be less than 100%");
        }
    }
}
