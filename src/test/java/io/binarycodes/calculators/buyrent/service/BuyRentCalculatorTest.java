package io.binarycodes.calculators.buyrent.service;

import io.binarycodes.calculators.buyrent.domain.BuyRentInputs;
import io.binarycodes.calculators.buyrent.domain.BuyRentResult;
import io.binarycodes.calculators.buyrent.domain.BuyRentYear;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuyRentCalculatorTest {

    /**
     * Baseline INR-like scenario: ₹50L home, 20% down, 20-year loan at 8.5%.
     * Both CGT rates are zero by default so tests that don't care about tax work
     * on clean pre-tax numbers; individual tests set non-zero rates as needed.
     */
    private static BuyRentInputs base() {
        final var inputs = new BuyRentInputs();
        inputs.setHomePrice(new BigDecimal("5000000"));
        inputs.setDownPaymentPct(new BigDecimal("20"));
        inputs.setLoanTermYears(20);
        inputs.setMortgageRatePct(new BigDecimal("8.5"));
        inputs.setPropertyTaxRatePct(new BigDecimal("0.5"));
        inputs.setMaintenancePct(new BigDecimal("1.5"));
        inputs.setAppreciationPct(new BigDecimal("6"));
        inputs.setBuyingCostPct(new BigDecimal("7"));
        inputs.setSellingCostPct(new BigDecimal("2"));
        inputs.setMonthlyRent(new BigDecimal("25000"));
        inputs.setRentIncreasePct(new BigDecimal("5"));
        inputs.setInvestmentReturnPct(new BigDecimal("10"));
        inputs.setInflationRatePct(new BigDecimal("6"));
        inputs.setAnalysisYears(20);
        inputs.setPropertyCapitalGainsTaxPct(BigDecimal.ZERO);
        inputs.setInvestmentGainsTaxPct(BigDecimal.ZERO);
        return inputs;
    }

    // -------------------------------------------------------------------------
    // Schedule structure
    // -------------------------------------------------------------------------

    @Test
    void row_count_equals_analysis_years() {
        assertEquals(20, BuyRentCalculator.calculate(base()).rows().size());
    }

    @Test
    void year_numbers_are_sequential_from_one() {
        final BuyRentResult result = BuyRentCalculator.calculate(base());
        for (int index = 0; index < result.rows().size(); index++) {
            assertEquals(index + 1, result.rows().get(index).year());
        }
    }

    @Test
    void single_year_analysis_produces_one_row() {
        final BuyRentInputs inputs = base();
        inputs.setAnalysisYears(1);
        assertEquals(1, BuyRentCalculator.calculate(inputs).rows().size());
    }

    // -------------------------------------------------------------------------
    // EMI / mortgage
    // -------------------------------------------------------------------------

    @Test
    void zero_mortgage_rate_emi_equals_principal_divided_by_months() {
        // P = 1 200 000, 0% rate, 10 years → EMI = 1 200 000 / 120 = 10 000 exactly.
        final BuyRentInputs inputs = base();
        inputs.setHomePrice(new BigDecimal("1200000"));
        inputs.setDownPaymentPct(BigDecimal.ZERO);
        inputs.setLoanTermYears(10);
        inputs.setMortgageRatePct(BigDecimal.ZERO);
        final BuyRentResult result = BuyRentCalculator.calculate(inputs);
        assertEquals(0, result.monthlyEmi().compareTo(new BigDecimal("10000")));
    }

    @Test
    void rent_steps_once_per_year_rather_than_escalating_every_month() {
        // Rent 25,000/mo, +5%/yr. Stepped annually, year 1 is flat at the initial
        // rate (12 × 25,000) and year 2 jumps to 25,000 × 1.05 (12 × 26,250).
        final BuyRentResult result = BuyRentCalculator.calculate(base());
        final BigDecimal yearOneRent = result.rows().get(0).cumulativeRentPaid();
        final BigDecimal yearTwoRent = result.rows().get(1).cumulativeRentPaid();
        assertEquals(0, yearOneRent.compareTo(new BigDecimal("300000")),
                "year-1 rent must be flat at the initial rate, not continuously escalated");
        assertEquals(0, yearTwoRent.subtract(yearOneRent).compareTo(new BigDecimal("315000")),
                "year-2 rent must step to 26,250/month (25,000 × 1.05)");
    }

    @Test
    void mortgage_balance_reaches_zero_by_end_of_loan_term() {
        // 20-year loan, 20-year analysis → balance fully paid by last row.
        final BuyRentYear lastRow = lastRow(BuyRentCalculator.calculate(base()));
        // Allow for sub-unit rounding from monthly DECIMAL64 arithmetic.
        assertTrue(lastRow.mortgageBalance().compareTo(BigDecimal.ONE) <= 0,
                "mortgage balance should be < 1 currency unit at loan term end");
    }

    @Test
    void mortgage_balance_is_zero_after_loan_term_ends_early() {
        // 10-year loan inside a 20-year analysis.
        final BuyRentInputs inputs = base();
        inputs.setLoanTermYears(10);
        inputs.setAnalysisYears(20);
        final BuyRentResult result = BuyRentCalculator.calculate(inputs);

        final BuyRentYear year10 = result.rows().get(9);
        assertTrue(year10.mortgageBalance().compareTo(BigDecimal.ONE) <= 0,
                "balance should clear by year 10");
        // Still zero a decade later.
        assertEquals(0, result.rows().get(19).mortgageBalance().compareTo(BigDecimal.ZERO));
    }

    @Test
    void initial_monthly_cost_buy_exceeds_emi_alone_and_matches_first_rent() {
        // initialMonthlyCostBuy = EMI + homePrice × (propTax + maint) / 12.
        final BuyRentResult result = BuyRentCalculator.calculate(base());
        assertTrue(result.initialMonthlyCostBuy().compareTo(result.monthlyEmi()) > 0,
                "buy cost must exceed EMI alone (property tax + maintenance on top)");
        assertEquals(0, result.initialMonthlyCostRent().compareTo(new BigDecimal("25000")));
    }

    // -------------------------------------------------------------------------
    // Costs and home value
    // -------------------------------------------------------------------------

    @Test
    void cumulative_costs_are_monotonically_increasing() {
        final BuyRentResult result = BuyRentCalculator.calculate(base());
        BigDecimal prevRent = BigDecimal.ZERO;
        BigDecimal prevBuy = BigDecimal.ZERO;
        for (final BuyRentYear row : result.rows()) {
            assertTrue(row.cumulativeRentPaid().compareTo(prevRent) > 0);
            assertTrue(row.cumulativeBuyCost().compareTo(prevBuy) > 0);
            prevRent = row.cumulativeRentPaid();
            prevBuy = row.cumulativeBuyCost();
        }
    }

    @Test
    void home_value_grows_with_appreciation() {
        // 6% annual compound over 20 years ≈ 3.2× the purchase price.
        final BuyRentResult result = BuyRentCalculator.calculate(base());
        final BigDecimal minimumExpected = new BigDecimal("5000000")
                .multiply(new BigDecimal("3.2"), java.math.MathContext.DECIMAL64);
        assertTrue(result.homeValueAtHorizon().compareTo(minimumExpected) > 0);
    }

    @Test
    void selling_costs_reduce_equity_below_home_value_every_year() {
        final BuyRentResult result = BuyRentCalculator.calculate(base());
        for (final BuyRentYear row : result.rows()) {
            assertTrue(row.equity().compareTo(row.homeValue()) < 0,
                    "equity (net of sell costs) must be less than gross home value at year " + row.year());
        }
    }

    @Test
    void higher_appreciation_increases_home_value_and_equity_at_horizon() {
        final BuyRentInputs low = base();
        low.setAppreciationPct(new BigDecimal("2"));
        final BuyRentInputs high = base();
        high.setAppreciationPct(new BigDecimal("12"));

        final BuyRentResult lowResult = BuyRentCalculator.calculate(low);
        final BuyRentResult highResult = BuyRentCalculator.calculate(high);

        assertTrue(highResult.homeValueAtHorizon().compareTo(lowResult.homeValueAtHorizon()) > 0);
        assertTrue(highResult.equityAtHorizon().compareTo(lowResult.equityAtHorizon()) > 0);
    }

    @Test
    void higher_investment_return_grows_rent_portfolio_more_at_horizon() {
        final BuyRentInputs low = base();
        low.setInvestmentReturnPct(new BigDecimal("3"));
        final BuyRentInputs high = base();
        high.setInvestmentReturnPct(new BigDecimal("18"));

        assertTrue(BuyRentCalculator.calculate(high).rentPortfolioAtHorizon()
                .compareTo(BuyRentCalculator.calculate(low).rentPortfolioAtHorizon()) > 0);
    }

    @Test
    void rent_portfolio_shrinks_when_rent_far_exceeds_buy_costs() {
        // Monthly rent >> buy costs creates a persistent negative surplus (withdrawal).
        // With a low investment return the portfolio cannot outrun the withdrawals.
        final BuyRentInputs inputs = base();
        inputs.setMortgageRatePct(new BigDecimal("1")); // low rate → low EMI
        inputs.setLoanTermYears(30);
        inputs.setPropertyTaxRatePct(BigDecimal.ZERO);
        inputs.setMaintenancePct(BigDecimal.ZERO);
        inputs.setMonthlyRent(new BigDecimal("50000")); // rent >> buy cost (~13k)
        inputs.setInvestmentReturnPct(new BigDecimal("1"));
        inputs.setAnalysisYears(1);

        // Initial investment = 5M × 20% down + 5M × 7% buying costs = 1.35M.
        final BigDecimal initialInvestment = new BigDecimal("1350000");
        final BuyRentYear year1 = BuyRentCalculator.calculate(inputs).rows().get(0);
        assertTrue(year1.rentPortfolio().compareTo(initialInvestment) < 0,
                "portfolio must shrink when rent far exceeds buy costs each month");
    }

    // -------------------------------------------------------------------------
    // Capital gains tax
    // -------------------------------------------------------------------------

    @Test
    void zero_cgt_rates_leave_after_tax_values_equal_to_pre_tax() {
        // base() already has both CGT rates = 0.
        final BuyRentResult result = BuyRentCalculator.calculate(base());
        for (final BuyRentYear row : result.rows()) {
            assertEquals(0, row.equity().compareTo(row.equityAfterTax()),
                    "equityAfterTax must equal equity when property CGT = 0 at year " + row.year());
            assertEquals(0, row.rentPortfolio().compareTo(row.rentPortfolioAfterTax()),
                    "rentPortfolioAfterTax must equal rentPortfolio when investment CGT = 0 at year " + row.year());
        }
    }

    @Test
    void property_cgt_is_deducted_when_home_sells_above_cost_basis() {
        // 100% annual appreciation with no sell costs isolates the property CGT.
        final BuyRentInputs inputs = base();
        inputs.setAppreciationPct(new BigDecimal("100"));
        inputs.setSellingCostPct(BigDecimal.ZERO);
        inputs.setBuyingCostPct(BigDecimal.ZERO);
        inputs.setPropertyCapitalGainsTaxPct(new BigDecimal("20"));
        inputs.setAnalysisYears(1);
        final BuyRentYear year1 = BuyRentCalculator.calculate(inputs).rows().get(0);
        assertTrue(year1.equity().compareTo(year1.equityAfterTax()) > 0,
                "CGT must reduce equity when selling above cost basis");
    }

    @Test
    void property_cgt_not_applied_when_home_sells_below_cost_basis() {
        // High buying costs inflate the cost basis above the flat home price.
        final BuyRentInputs inputs = base();
        inputs.setAppreciationPct(BigDecimal.ZERO);  // no appreciation → sale proceeds = home price
        inputs.setBuyingCostPct(new BigDecimal("10")); // cost basis = home price × 1.10 > sale proceeds
        inputs.setSellingCostPct(BigDecimal.ZERO);
        inputs.setPropertyCapitalGainsTaxPct(new BigDecimal("30"));
        inputs.setAnalysisYears(1);
        final BuyRentYear year1 = BuyRentCalculator.calculate(inputs).rows().get(0);
        assertEquals(0, year1.equity().compareTo(year1.equityAfterTax()),
                "no CGT when profit is zero or negative");
    }

    @Test
    void buying_costs_raise_cgt_basis_and_increase_after_tax_equity() {
        // Higher buying costs → higher cost basis → lower taxable gain → lower tax → higher net equity.
        // Both scenarios have identical appreciation so the home value and pre-tax equity are the same.
        final BuyRentInputs noCost = base();
        noCost.setBuyingCostPct(BigDecimal.ZERO);
        noCost.setPropertyCapitalGainsTaxPct(new BigDecimal("20"));

        final BuyRentInputs withCost = base();
        withCost.setBuyingCostPct(new BigDecimal("10"));
        withCost.setPropertyCapitalGainsTaxPct(new BigDecimal("20"));

        // With 10% buying costs: CGT basis is 500k higher (10% of 5M).
        // Property CGT saved = 500k × 20% = 100k. After-tax equity increases by 100k.
        final BigDecimal noCostAfterTax = lastRow(BuyRentCalculator.calculate(noCost)).equityAfterTax();
        final BigDecimal withCostAfterTax = lastRow(BuyRentCalculator.calculate(withCost)).equityAfterTax();
        final BigDecimal expectedDifference = new BigDecimal("100000");
        final BigDecimal actualDifference = withCostAfterTax.subtract(noCostAfterTax).abs();
        assertEquals(0, actualDifference.compareTo(expectedDifference), actualDifference.toPlainString());
    }

    @Test
    void investment_cgt_is_deducted_when_portfolio_has_gains() {
        final BuyRentInputs inputs = base();
        inputs.setInvestmentGainsTaxPct(new BigDecimal("20"));
        final BuyRentResult result = BuyRentCalculator.calculate(inputs);

        boolean gainFound = false;
        for (final BuyRentYear row : result.rows()) {
            if (row.rentPortfolio().compareTo(row.rentPortfolioAfterTax()) > 0) {
                gainFound = true;
                assertTrue(row.rentPortfolioAfterTax().compareTo(row.rentPortfolio()) < 0,
                        "after-tax portfolio must be less than pre-tax when CGT > 0");
            }
        }
        assertTrue(gainFound, "portfolio should generate taxable gains over 20 years at 10%");
    }

    @Test
    void higher_investment_cgt_reduces_after_tax_portfolio_more() {
        final BuyRentInputs low = base();
        low.setInvestmentGainsTaxPct(new BigDecimal("10"));
        final BuyRentInputs high = base();
        high.setInvestmentGainsTaxPct(new BigDecimal("40"));

        assertTrue(
                BuyRentCalculator.calculate(low).rentPortfolioAtHorizonAfterTax()
                        .compareTo(BuyRentCalculator.calculate(high).rentPortfolioAtHorizonAfterTax()) > 0,
                "lower CGT must leave a larger after-tax portfolio");
    }

    @Test
    void higher_property_cgt_reduces_after_tax_equity_more() {
        final BuyRentInputs low = base();
        low.setPropertyCapitalGainsTaxPct(new BigDecimal("10"));
        final BuyRentInputs high = base();
        high.setPropertyCapitalGainsTaxPct(new BigDecimal("40"));

        assertTrue(
                BuyRentCalculator.calculate(low).equityAtHorizonAfterTax()
                        .compareTo(BuyRentCalculator.calculate(high).equityAtHorizonAfterTax()) > 0,
                "lower property CGT must leave higher after-tax equity");
    }

    // -------------------------------------------------------------------------
    // Net difference and break-even
    // -------------------------------------------------------------------------

    @Test
    void net_difference_is_equity_after_tax_minus_portfolio_after_tax() {
        final BuyRentInputs inputs = base();
        inputs.setPropertyCapitalGainsTaxPct(new BigDecimal("20"));
        inputs.setInvestmentGainsTaxPct(new BigDecimal("15"));
        final BuyRentResult result = BuyRentCalculator.calculate(inputs);
        for (final BuyRentYear row : result.rows()) {
            final BigDecimal expected = row.equityAfterTax().subtract(row.rentPortfolioAfterTax());
            assertEquals(0, row.netDifference().compareTo(expected),
                    "netDifference mismatch at year " + row.year());
        }
    }

    @Test
    void break_even_is_first_year_after_tax_buy_leads() {
        // Force buy to win early: very high appreciation, very low investment return.
        final BuyRentInputs inputs = base();
        inputs.setAppreciationPct(new BigDecimal("15"));
        inputs.setInvestmentReturnPct(new BigDecimal("3"));
        inputs.setPropertyCapitalGainsTaxPct(BigDecimal.ZERO);
        inputs.setInvestmentGainsTaxPct(BigDecimal.ZERO);
        final BuyRentResult result = BuyRentCalculator.calculate(inputs);

        final int breakEvenYear = result.breakEvenYear();
        assertTrue(breakEvenYear > 0, "high appreciation should produce a break-even year");

        for (final BuyRentYear row : result.rows()) {
            if (row.year() < breakEvenYear) {
                assertTrue(row.equityAfterTax().compareTo(row.rentPortfolioAfterTax()) < 0,
                        "buy should still be behind before year " + breakEvenYear);
            } else if (row.year() == breakEvenYear) {
                assertTrue(row.equityAfterTax().compareTo(row.rentPortfolioAfterTax()) >= 0,
                        "buy should be ahead at the break-even year");
                break;
            }
        }
    }

    @Test
    void no_break_even_when_investment_return_far_exceeds_appreciation() {
        final BuyRentInputs inputs = base();
        inputs.setAppreciationPct(BigDecimal.ZERO);
        inputs.setInvestmentReturnPct(new BigDecimal("20"));
        assertEquals(-1, BuyRentCalculator.calculate(inputs).breakEvenYear());
    }

    @Test
    void tax_can_push_break_even_later_than_pre_tax() {
        // A scenario that has a break-even without tax; adding tax may delay or eliminate it.
        final BuyRentInputs noTax = base();
        noTax.setAppreciationPct(new BigDecimal("15"));
        noTax.setInvestmentReturnPct(new BigDecimal("3"));
        noTax.setPropertyCapitalGainsTaxPct(BigDecimal.ZERO);
        noTax.setInvestmentGainsTaxPct(BigDecimal.ZERO);

        final BuyRentInputs withTax = base();
        withTax.setAppreciationPct(new BigDecimal("15"));
        withTax.setInvestmentReturnPct(new BigDecimal("3"));
        withTax.setPropertyCapitalGainsTaxPct(new BigDecimal("30")); // hurts the buy path
        withTax.setInvestmentGainsTaxPct(BigDecimal.ZERO);           // rent path unaffected

        final int breakEvenNoTax = BuyRentCalculator.calculate(noTax).breakEvenYear();
        final int breakEvenWithTax = BuyRentCalculator.calculate(withTax).breakEvenYear();

        // Property CGT reduces after-tax equity, so break-even is the same or later with tax.
        assertTrue(breakEvenWithTax == -1 || breakEvenWithTax >= breakEvenNoTax,
                "property CGT should not move the break-even earlier");
    }

    // -------------------------------------------------------------------------
    // Result summary fields
    // -------------------------------------------------------------------------

    @Test
    void result_horizon_fields_match_last_row() {
        final BuyRentInputs inputs = base();
        inputs.setPropertyCapitalGainsTaxPct(new BigDecimal("20"));
        inputs.setInvestmentGainsTaxPct(new BigDecimal("15"));
        final BuyRentResult result = BuyRentCalculator.calculate(inputs);
        final BuyRentYear last = lastRow(result);

        assertEquals(0, result.homeValueAtHorizon().compareTo(last.homeValue()));
        assertEquals(0, result.equityAtHorizon().compareTo(last.equity()));
        assertEquals(0, result.equityAtHorizonAfterTax().compareTo(last.equityAfterTax()));
        assertEquals(0, result.rentPortfolioAtHorizon().compareTo(last.rentPortfolio()));
        assertEquals(0, result.rentPortfolioAtHorizonAfterTax().compareTo(last.rentPortfolioAfterTax()));
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    @Test
    void rejects_non_positive_home_price() {
        final BuyRentInputs zero = base();
        zero.setHomePrice(BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> BuyRentCalculator.calculate(zero));

        final BuyRentInputs negative = base();
        negative.setHomePrice(new BigDecimal("-1"));
        assertThrows(IllegalArgumentException.class, () -> BuyRentCalculator.calculate(negative));
    }

    @Test
    void rejects_non_positive_monthly_rent() {
        final BuyRentInputs inputs = base();
        inputs.setMonthlyRent(BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> BuyRentCalculator.calculate(inputs));
    }

    @Test
    void rejects_zero_or_negative_analysis_years() {
        final BuyRentInputs zero = base();
        zero.setAnalysisYears(0);
        assertThrows(IllegalArgumentException.class, () -> BuyRentCalculator.calculate(zero));
    }

    @Test
    void rejects_down_payment_at_or_above_100_percent() {
        final BuyRentInputs full = base();
        full.setDownPaymentPct(new BigDecimal("100"));
        assertThrows(IllegalArgumentException.class, () -> BuyRentCalculator.calculate(full));

        final BuyRentInputs over = base();
        over.setDownPaymentPct(new BigDecimal("120"));
        assertThrows(IllegalArgumentException.class, () -> BuyRentCalculator.calculate(over));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static BuyRentYear lastRow(BuyRentResult result) {
        return result.rows().get(result.rows().size() - 1);
    }
}
