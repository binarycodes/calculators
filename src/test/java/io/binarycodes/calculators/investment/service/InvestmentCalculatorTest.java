package io.binarycodes.calculators.investment.service;

import io.binarycodes.calculators.base.common.TimeHorizonMode;
import io.binarycodes.calculators.investment.domain.ContributionFrequency;
import io.binarycodes.calculators.investment.domain.InvestmentInputs;
import io.binarycodes.calculators.investment.domain.InvestmentResult;
import io.binarycodes.calculators.investment.domain.InvestmentYear;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestmentCalculatorTest {

    private static final MathContext MC = MathContext.DECIMAL64;

    private static InvestmentInputs base(int investYears, int holdYears) {
        final var inputs = new InvestmentInputs();
        inputs.setAmount(new BigDecimal("10000"));
        inputs.setFrequency(ContributionFrequency.MONTHLY);
        inputs.setGrowthRatePct(BigDecimal.valueOf(12));
        inputs.setTaxRatePct(BigDecimal.ZERO);
        inputs.setInflationRatePct(BigDecimal.ZERO);
        inputs.setStepUpPct(BigDecimal.ZERO);
        inputs.setHorizonMode(TimeHorizonMode.YEARS);
        inputs.setInvestYears(investYears);
        inputs.setInvestMonths(0);
        inputs.setHoldYears(holdYears);
        inputs.setHoldMonths(0);
        return inputs;
    }

    @Test
    void invested_equals_contributions_no_stepup() {
        final InvestmentResult result = InvestmentCalculator.calculate(base(10, 0));
        // 10 years × 12 months × 10,000.
        assertEquals(0, result.totalInvested().compareTo(new BigDecimal("1200000")));
        assertEquals(120, result.investmentMonths());
        assertEquals(0, result.holdMonths());
    }

    @Test
    void yearly_frequency_contributes_twelve_times_less_often() {
        final InvestmentInputs monthly = base(10, 0);
        final InvestmentInputs yearly = base(10, 0);
        yearly.setFrequency(ContributionFrequency.YEARLY);
        // Yearly invests amount once per year → 10 × 10,000 = 100,000 principal.
        assertEquals(0, InvestmentCalculator.calculate(yearly).totalInvested()
                .compareTo(new BigDecimal("100000")));
        // Monthly invests 12× as much principal.
        assertTrue(InvestmentCalculator.calculate(monthly).totalInvested()
                .compareTo(InvestmentCalculator.calculate(yearly).totalInvested()) > 0);
    }

    @Test
    void hold_phase_grows_corpus_without_new_principal() {
        final InvestmentResult noHold = InvestmentCalculator.calculate(base(10, 0));
        final InvestmentResult withHold = InvestmentCalculator.calculate(base(10, 5));
        // Same contributions, so principal is identical...
        assertEquals(0, noHold.totalInvested().compareTo(withHold.totalInvested()));
        // ...but the extra 5 years of compounding lifts the maturity value.
        assertTrue(withHold.maturityValue().compareTo(noHold.maturityValue()) > 0);
        assertEquals(60, withHold.holdMonths());
    }

    @Test
    void gains_and_net_reconcile() {
        final InvestmentInputs inputs = base(15, 0);
        inputs.setTaxRatePct(new BigDecimal("20"));
        final InvestmentResult result = InvestmentCalculator.calculate(inputs);
        assertEquals(0, result.gains().compareTo(
                result.maturityValue().subtract(result.totalInvested(), MC)));
        final BigDecimal expectedTax = result.gains().multiply(new BigDecimal("0.20"), MC);
        assertTrue(result.taxAtExit().subtract(expectedTax, MC).abs()
                .compareTo(new BigDecimal("0.01")) < 0);
        assertEquals(0, result.netValue().compareTo(
                result.maturityValue().subtract(result.taxAtExit(), MC)));
    }

    @Test
    void buying_power_discounts_net_by_inflation() {
        final InvestmentInputs inputs = base(10, 0);
        inputs.setInflationRatePct(BigDecimal.valueOf(6));
        final InvestmentResult result = InvestmentCalculator.calculate(inputs);
        final BigDecimal expected = result.netValue()
                .divide(BigDecimal.valueOf(Math.pow(1.06, 10)), MC);
        assertTrue(result.buyingPowerToday().subtract(expected, MC).abs()
                .compareTo(new BigDecimal("0.01")) < 0);
        assertTrue(result.buyingPowerToday().compareTo(result.netValue()) < 0);
    }

    @Test
    void zero_inflation_buying_power_equals_net() {
        final InvestmentResult result = InvestmentCalculator.calculate(base(10, 3));
        assertEquals(0, result.buyingPowerToday().compareTo(result.netValue()));
    }

    @Test
    void step_up_raises_invested_total() {
        final InvestmentInputs flat = base(10, 0);
        final InvestmentInputs stepped = base(10, 0);
        stepped.setStepUpPct(BigDecimal.valueOf(10));
        assertTrue(InvestmentCalculator.calculate(stepped).totalInvested()
                .compareTo(InvestmentCalculator.calculate(flat).totalInvested()) > 0);
    }

    @Test
    void rows_span_full_horizon_and_phases_split() {
        final InvestmentResult result = InvestmentCalculator.calculate(base(10, 5));
        assertEquals(15, result.rows().size());
        // First 10 years investing, last 5 holding.
        for (int yearIndex = 0; yearIndex < 15; yearIndex++) {
            final InvestmentYear row = result.rows().get(yearIndex);
            final InvestmentYear.Phase expected = yearIndex < 10
                    ? InvestmentYear.Phase.INVESTING
                    : InvestmentYear.Phase.HOLDING;
            assertEquals(expected, row.phase(), "year index " + yearIndex);
        }
        // Principal stops growing once contributions stop.
        assertEquals(0, result.rows().get(10).principal().compareTo(
                result.rows().get(14).principal()));
    }

    @Test
    void ages_horizon_drives_investment_phase() {
        final InvestmentInputs inputs = base(0, 0);
        inputs.setHorizonMode(TimeHorizonMode.AGES);
        inputs.setInvestYears(null);
        inputs.setCurrentAge(35);
        inputs.setGoalAge(50);
        final InvestmentResult result = InvestmentCalculator.calculate(inputs);
        assertEquals(180, result.investmentMonths());
    }

    @Test
    void zero_investment_time_rejected() {
        final InvestmentInputs inputs = base(0, 5);
        inputs.setInvestMonths(0);
        assertThrows(IllegalArgumentException.class, () -> InvestmentCalculator.calculate(inputs));
    }

    @Test
    void hold_months_outside_zero_to_eleven_rejected() {
        final InvestmentInputs twelve = base(5, 0);
        twelve.setHoldMonths(12);
        assertThrows(IllegalArgumentException.class, () -> InvestmentCalculator.calculate(twelve));

        final InvestmentInputs negative = base(5, 0);
        negative.setHoldMonths(-1);
        assertThrows(IllegalArgumentException.class, () -> InvestmentCalculator.calculate(negative));

        // 11 is the maximum valid value.
        final InvestmentInputs eleven = base(5, 0);
        eleven.setHoldMonths(11);
        assertDoesNotThrow(() -> InvestmentCalculator.calculate(eleven));
    }

    @Test
    void null_hold_years_and_months_default_to_zero() {
        final InvestmentInputs inputs = base(5, 0);
        inputs.setHoldYears(null);
        inputs.setHoldMonths(null);
        final InvestmentResult result = InvestmentCalculator.calculate(inputs);
        assertEquals(0, result.holdMonths());
    }

    @Test
    void null_frequency_defaults_to_monthly() {
        // null frequency → MONTHLY → 12 × 10,000 = 120,000 invested per year.
        final InvestmentInputs inputs = base(1, 0);
        inputs.setFrequency(null);
        final InvestmentResult result = InvestmentCalculator.calculate(inputs);
        assertEquals(0, result.totalInvested().compareTo(new BigDecimal("120000")));
    }

    @Test
    void zero_growth_rate_means_no_gains_and_no_tax() {
        // With 0% growth there are no gains, so the 30% tax rate must produce zero tax.
        final InvestmentInputs inputs = base(10, 0);
        inputs.setGrowthRatePct(BigDecimal.ZERO);
        inputs.setTaxRatePct(new BigDecimal("30"));
        final InvestmentResult result = InvestmentCalculator.calculate(inputs);
        assertEquals(0, result.gains().compareTo(BigDecimal.ZERO));
        assertEquals(0, result.taxAtExit().compareTo(BigDecimal.ZERO));
        assertEquals(0, result.netValue().compareTo(result.maturityValue()));
        assertEquals(0, result.maturityValue().compareTo(result.totalInvested()));
    }
}
