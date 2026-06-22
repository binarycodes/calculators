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
 * value; home appreciates at the stated rate.</p>
 *
 * <p><b>Rent path</b> — the down payment + buying costs that were not spent are
 * invested immediately at the investment return rate; each month any surplus
 * (buy costs > rent) is added to the portfolio and any deficit (rent > buy
 * costs) is withdrawn; rent grows annually.</p>
 *
 * <p>Break-even is the first year where buy equity ≥ rent portfolio.</p>
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

        // The rent-path portfolio starts with the capital not spent on the purchase.
        final BigDecimal initialInvestment = homePrice.multiply(downFraction, Rates.CONTEXT)
                .add(homePrice.multiply(buyingCostFraction, Rates.CONTEXT), Rates.CONTEXT);

        BigDecimal homeValue = homePrice;
        BigDecimal mortgageBalance = loanAmount;
        BigDecimal rentPortfolio = initialInvestment;
        BigDecimal cumulativeRentPaid = BigDecimal.ZERO;
        BigDecimal cumulativeBuyCost = BigDecimal.ZERO;
        BigDecimal inflationAccumulator = BigDecimal.ONE;

        final BigDecimal initialMonthlyRent = inputs.getMonthlyRent();
        final BigDecimal initialMonthlyCostBuy = computeInitialMonthlyCostBuy(
                monthlyEmi, homePrice, propertyTaxFraction, maintenanceFraction);

        final int totalMonths = (inputs.getAnalysisYears() == null ? 0 : inputs.getAnalysisYears()) * 12;
        final List<BuyRentYear> rows = new ArrayList<>(inputs.getAnalysisYears() == null ? 0 : inputs.getAnalysisYears());
        int breakEvenYear = -1;

        for (int month = 1; month <= totalMonths; month++) {
            // Advance home value and inflation accumulator.
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
                // Zero-rate loan: divide principal evenly.
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

            // Rent path — grow portfolio, add/subtract surplus.
            final double yearFraction = (double) (month - 1) / 12.0;
            final BigDecimal monthlyRentNow = initialMonthlyRent.multiply(
                    BigDecimal.valueOf(Math.pow(1.0 + rentIncreaseFraction.doubleValue(), yearFraction)),
                    Rates.CONTEXT);
            cumulativeRentPaid = cumulativeRentPaid.add(monthlyRentNow, Rates.CONTEXT);

            rentPortfolio = rentPortfolio.multiply(
                    BigDecimal.ONE.add(monthlyInvestmentRate, Rates.CONTEXT), Rates.CONTEXT);
            final BigDecimal surplus = totalBuyCostThisMonth.subtract(monthlyRentNow, Rates.CONTEXT);
            rentPortfolio = rentPortfolio.add(surplus, Rates.CONTEXT);

            // Snapshot at each year boundary.
            if (month % 12 == 0) {
                final int year = month / 12;
                final BigDecimal currentMortgageBalance = mortgageBalance.max(BigDecimal.ZERO);
                final BigDecimal equity = homeValue
                        .multiply(BigDecimal.ONE.subtract(sellingCostFraction, Rates.CONTEXT), Rates.CONTEXT)
                        .subtract(currentMortgageBalance, Rates.CONTEXT);
                final BigDecimal netDiff = equity.subtract(rentPortfolio, Rates.CONTEXT);
                final BigDecimal realNetDiff = netDiff.divide(inflationAccumulator, Rates.CONTEXT);

                rows.add(new BuyRentYear(
                        year,
                        homeValue,
                        currentMortgageBalance,
                        equity,
                        rentPortfolio,
                        cumulativeRentPaid,
                        cumulativeBuyCost,
                        netDiff,
                        realNetDiff));

                if (breakEvenYear < 0 && equity.compareTo(rentPortfolio) >= 0) {
                    breakEvenYear = year;
                }
            }
        }

        final BuyRentYear lastRow = rows.isEmpty() ? null : rows.get(rows.size() - 1);
        return new BuyRentResult(
                monthlyEmi,
                initialMonthlyCostBuy,
                initialMonthlyRent,
                lastRow == null ? homePrice : lastRow.homeValue(),
                lastRow == null ? BigDecimal.ZERO : lastRow.equity(),
                lastRow == null ? initialInvestment : lastRow.rentPortfolio(),
                breakEvenYear,
                rows);
    }

    private static BigDecimal computeEmi(BigDecimal principal, BigDecimal monthlyRate, int months) {
        if (months <= 0 || principal.signum() == 0) {
            return BigDecimal.ZERO;
        }
        if (monthlyRate.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(months), Rates.CONTEXT);
        }
        // Standard amortisation: P × r × (1+r)^n / ((1+r)^n − 1)
        final BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate, Rates.CONTEXT);
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
