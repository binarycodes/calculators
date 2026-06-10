package io.binarycodes.calculators.inflation.service;

import io.binarycodes.calculators.base.common.TimeHorizonMode;
import io.binarycodes.calculators.inflation.domain.InflationInputs;
import io.binarycodes.calculators.inflation.domain.InflationResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Year;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InflationCalculatorTest {

    private static final MathContext MC = MathContext.DECIMAL64;

    private static InflationInputs base(int years, boolean amountIsToday) {
        final var inputs = new InflationInputs();
        inputs.setHorizonMode(TimeHorizonMode.YEARS);
        inputs.setYearsToGoal(years);
        inputs.setMonthsToGoal(0);
        inputs.setAmount(new BigDecimal("1000000"));
        inputs.setInflationRatePct(BigDecimal.valueOf(6));
        inputs.setAmountIsToday(amountIsToday);
        return inputs;
    }

    @Test
    void forward_projection_inflates_amount() {
        final InflationResult result = InflationCalculator.calculate(base(10, true));
        final BigDecimal expected = new BigDecimal("1000000")
                .multiply(BigDecimal.valueOf(Math.pow(1.06, 10)), MC);
        assertTrue(result.resultAmount().subtract(expected, MC).abs()
                        .compareTo(new BigDecimal("0.01")) < 0,
                "forward projection should be amount × (1+i)^years; got " + result.resultAmount());
        assertTrue(result.resultAmount().compareTo(result.inputAmount()) > 0);
    }

    @Test
    void backward_projection_discounts_amount() {
        final InflationResult result = InflationCalculator.calculate(base(10, false));
        final BigDecimal expected = new BigDecimal("1000000")
                .divide(BigDecimal.valueOf(Math.pow(1.06, 10)), MC);
        assertTrue(result.resultAmount().subtract(expected, MC).abs()
                        .compareTo(new BigDecimal("0.01")) < 0,
                "backward projection should be amount / (1+i)^years; got " + result.resultAmount());
        assertTrue(result.resultAmount().compareTo(result.inputAmount()) < 0);
    }

    @Test
    void forward_and_backward_are_inverses() {
        final InflationResult forward = InflationCalculator.calculate(base(15, true));
        final InflationInputs backInputs = base(15, false);
        backInputs.setAmount(forward.resultAmount());
        final InflationResult back = InflationCalculator.calculate(backInputs);
        // Discounting the inflated value should return the original amount.
        assertTrue(back.resultAmount().subtract(new BigDecimal("1000000"), MC).abs()
                        .compareTo(new BigDecimal("0.01")) < 0,
                "round trip should recover the original amount; got " + back.resultAmount());
    }

    @Test
    void progression_starts_at_today_value_and_ends_at_horizon_value() {
        final InflationResult forward = InflationCalculator.calculate(base(10, true));
        final var points = forward.progression();
        assertEquals(11, points.size(), "10 whole years → 11 yearly points (year 0..10)");
        // First point is today's value (the entered amount in forward mode).
        assertEquals(0, points.get(0).value().compareTo(forward.inputAmount()));
        // Last point matches the inflated result within rounding.
        assertTrue(points.get(points.size() - 1).value()
                .subtract(forward.resultAmount(), MC).abs()
                .compareTo(new BigDecimal("0.01")) < 0);
    }

    @Test
    void progression_anchors_at_today_value_in_backward_mode() {
        final InflationResult back = InflationCalculator.calculate(base(10, false));
        // First point equals today's value (the discounted result in backward mode).
        assertEquals(0, back.progression().get(0).value().compareTo(back.resultAmount()));
    }

    @Test
    void zero_inflation_leaves_amount_unchanged() {
        final InflationInputs inputs = base(20, true);
        inputs.setInflationRatePct(BigDecimal.ZERO);
        final InflationResult result = InflationCalculator.calculate(inputs);
        assertEquals(0, result.resultAmount().compareTo(result.inputAmount()));
    }

    @Test
    void fractional_horizon_compounds_partial_year() {
        final InflationInputs inputs = base(2, true);
        inputs.setMonthsToGoal(6); // 2.5 years
        final InflationResult result = InflationCalculator.calculate(inputs);
        final BigDecimal expected = new BigDecimal("1000000")
                .multiply(BigDecimal.valueOf(Math.pow(1.06, 2.5)), MC);
        assertEquals(30, result.totalMonths());
        assertTrue(result.resultAmount().subtract(expected, MC).abs()
                .compareTo(new BigDecimal("0.01")) < 0);
    }

    @Test
    void ages_horizon_resolves() {
        final InflationInputs inputs = base(0, true);
        inputs.setHorizonMode(TimeHorizonMode.AGES);
        inputs.setYearsToGoal(null);
        inputs.setCurrentAge(35);
        inputs.setGoalAge(50);
        final InflationResult result = InflationCalculator.calculate(inputs);
        assertEquals(180, result.totalMonths());
    }

    @Test
    void target_year_horizon_resolves() {
        final int currentYear = Year.now().getValue();
        final int currentMonth = java.time.LocalDate.now().getMonthValue();
        final InflationInputs inputs = base(0, true);
        inputs.setHorizonMode(TimeHorizonMode.TARGET_YEAR);
        inputs.setYearsToGoal(null);
        inputs.setTargetYear(currentYear + 8);
        inputs.setTargetMonth(currentMonth);
        final InflationResult result = InflationCalculator.calculate(inputs);
        assertEquals(96, result.totalMonths());
    }

    @Test
    void zero_horizon_rejected() {
        final InflationInputs inputs = base(0, true);
        inputs.setMonthsToGoal(0);
        assertThrows(IllegalArgumentException.class, () -> InflationCalculator.calculate(inputs));
    }

    @Test
    void negative_amount_rejected() {
        final InflationInputs inputs = base(10, true);
        inputs.setAmount(new BigDecimal("-1"));
        assertThrows(IllegalArgumentException.class, () -> InflationCalculator.calculate(inputs));
    }
}
