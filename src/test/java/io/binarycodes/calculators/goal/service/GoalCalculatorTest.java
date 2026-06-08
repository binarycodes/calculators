package io.binarycodes.calculators.goal.service;

import io.binarycodes.calculators.goal.domain.GoalInputs;
import io.binarycodes.calculators.goal.domain.GoalProjectionRow;
import io.binarycodes.calculators.goal.domain.GoalResult;
import io.binarycodes.calculators.goal.domain.Investment;
import io.binarycodes.calculators.goal.domain.TimeHorizonMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalCalculatorTest {

    private static final MathContext MC = MathContext.DECIMAL64;

    private static GoalInputs base(int years) {
        return base(years, BigDecimal.ZERO, BigDecimal.valueOf(12), BigDecimal.ZERO);
    }

    private static GoalInputs base(int years, BigDecimal corpus, BigDecimal growth, BigDecimal tax) {
        final var inputs = new GoalInputs();
        inputs.setHorizonMode(TimeHorizonMode.YEARS);
        inputs.setYearsToGoal(years);
        inputs.setMonthsToGoal(0);
        inputs.setGoalAmount(new BigDecimal("10000000"));
        final List<Investment> investments = new ArrayList<>();
        investments.add(new Investment("Solo", corpus, growth, tax,
                BigDecimal.valueOf(100), BigDecimal.ZERO));
        inputs.setInvestments(investments);
        return inputs;
    }

    @Test
    void zero_corpus_zero_tax_no_stepup_solves_to_goal_at_exit() {
        final int years = 20;
        final GoalResult result = GoalCalculator.calculate(base(years));

        // With zero tax, finalBalance must equal the goal within rounding tolerance.
        final BigDecimal difference = result.finalBalance()
                .subtract(new BigDecimal("10000000"), MC).abs();
        assertTrue(difference.compareTo(new BigDecimal("1")) < 0,
                "final balance should match the goal within 1 unit; got "
                        + result.finalBalance());

        assertFalse(result.goalAlreadyCovered());
        assertEquals(years, result.rows().size());
        assertEquals(years * 12, result.totalMonths());
    }

    @Test
    void net_at_exit_matches_goal_for_solved_case() {
        final GoalInputs inputs = base(15,
                new BigDecimal("500000"), BigDecimal.valueOf(12), new BigDecimal("12.5"));
        inputs.getInvestments().get(0).setStepUpPct(new BigDecimal("8"));

        final GoalResult result = GoalCalculator.calculate(inputs);

        final BigDecimal difference = result.netAtExit()
                .subtract(inputs.getGoalAmount(), MC).abs();
        assertTrue(difference.compareTo(new BigDecimal("1")) < 0,
                "net-at-exit should match the goal within rounding; difference = " + difference);
    }

    @Test
    void zero_growth_solves_linearly() {
        final GoalInputs inputs = base(10, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        // With 0% growth, 0 corpus, 0 tax: M · 120 = goal (120 monthly contributions).
        final GoalResult result = GoalCalculator.calculate(inputs);
        final BigDecimal expectedMonthly = inputs.getGoalAmount()
                .divide(BigDecimal.valueOf(120), 2, RoundingMode.HALF_UP);
        assertEquals(0, expectedMonthly.compareTo(
                result.monthlyInvestment().setScale(2, RoundingMode.HALF_UP)));
    }

    @Test
    void goal_already_covered_yields_zero_monthly() {
        final GoalInputs inputs = base(20,
                new BigDecimal("5000000"), BigDecimal.valueOf(12), BigDecimal.ZERO);
        inputs.setGoalAmount(new BigDecimal("1000000"));

        final GoalResult result = GoalCalculator.calculate(inputs);
        assertTrue(result.goalAlreadyCovered());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.monthlyInvestment()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.firstYearInvestment()));
    }

    @Test
    void multiple_investments_solve_to_goal() {
        final GoalInputs inputs = base(20);
        final List<Investment> investments = new ArrayList<>();
        investments.add(new Investment("Equity",
                new BigDecimal("30000"), BigDecimal.valueOf(10), new BigDecimal("12.5"),
                BigDecimal.valueOf(70), BigDecimal.ZERO));
        investments.add(new Investment("Debt",
                new BigDecimal("10000"), BigDecimal.valueOf(6), new BigDecimal("30"),
                BigDecimal.valueOf(30), BigDecimal.ZERO));
        inputs.setInvestments(investments);

        final GoalResult result = GoalCalculator.calculate(inputs);
        // Net-at-exit (sum across buckets, each taxed at its own rate) must hit the goal.
        final BigDecimal difference = result.netAtExit()
                .subtract(inputs.getGoalAmount(), MC).abs();
        assertTrue(difference.compareTo(new BigDecimal("1")) < 0,
                "net-at-exit should match the goal; difference = " + difference);
    }

    @Test
    void allocations_not_summing_to_100_rejected() {
        final GoalInputs inputs = base(20);
        final List<Investment> investments = new ArrayList<>();
        investments.add(new Investment("A",
                BigDecimal.ZERO, BigDecimal.valueOf(10), BigDecimal.ZERO,
                BigDecimal.valueOf(60), BigDecimal.ZERO));
        investments.add(new Investment("B",
                BigDecimal.ZERO, BigDecimal.valueOf(10), BigDecimal.ZERO,
                BigDecimal.valueOf(30), BigDecimal.ZERO));
        inputs.setInvestments(investments);
        assertThrows(IllegalArgumentException.class, () -> GoalCalculator.calculate(inputs));
    }

    @Test
    void empty_investments_list_rejected() {
        final GoalInputs inputs = base(20);
        inputs.setInvestments(new ArrayList<>());
        assertThrows(IllegalArgumentException.class, () -> GoalCalculator.calculate(inputs));
    }

    @Test
    void step_up_lowers_required_starting_monthly() {
        final GoalInputs flat = base(20);
        final GoalInputs steppedUp = base(20);
        steppedUp.getInvestments().get(0).setStepUpPct(BigDecimal.valueOf(10));

        final BigDecimal flatMonthly = GoalCalculator.calculate(flat).monthlyInvestment();
        final BigDecimal steppedMonthly = GoalCalculator.calculate(steppedUp).monthlyInvestment();
        assertTrue(steppedMonthly.compareTo(flatMonthly) < 0,
                "step-up should reduce the year-1 monthly contribution");
    }

    @Test
    void higher_growth_lowers_required_monthly() {
        final GoalInputs slow = base(15, BigDecimal.ZERO, BigDecimal.valueOf(6), BigDecimal.ZERO);
        final GoalInputs fast = base(15, BigDecimal.ZERO, BigDecimal.valueOf(14), BigDecimal.ZERO);

        final BigDecimal slowMonthly = GoalCalculator.calculate(slow).monthlyInvestment();
        final BigDecimal fastMonthly = GoalCalculator.calculate(fast).monthlyInvestment();
        assertTrue(fastMonthly.compareTo(slowMonthly) < 0);
    }

    @Test
    void longer_horizon_lowers_required_monthly() {
        final GoalInputs shortHorizon = base(10);
        final GoalInputs longHorizon = base(25);
        final BigDecimal shortMonthly = GoalCalculator.calculate(shortHorizon).monthlyInvestment();
        final BigDecimal longMonthly = GoalCalculator.calculate(longHorizon).monthlyInvestment();
        assertTrue(longMonthly.compareTo(shortMonthly) < 0);
    }

    @Test
    void projection_balance_at_final_row_matches_finalBalance() {
        final GoalInputs inputs = base(15,
                new BigDecimal("250000"), BigDecimal.valueOf(12), BigDecimal.ZERO);
        inputs.getInvestments().get(0).setStepUpPct(BigDecimal.valueOf(5));
        final GoalResult result = GoalCalculator.calculate(inputs);

        final GoalProjectionRow finalRow = result.rows().get(result.rows().size() - 1);
        assertEquals(0, finalRow.balance().compareTo(result.finalBalance()),
                "last row's balance must equal the headline final balance");
        assertEquals(0, finalRow.principal().compareTo(result.finalPrincipal()));
        assertEquals(0, finalRow.gains().compareTo(result.finalGains()));
    }

    @Test
    void ages_horizon_resolves_to_yearsToGoal() {
        final GoalInputs inputs = base(0);
        inputs.setHorizonMode(TimeHorizonMode.AGES);
        inputs.setYearsToGoal(null);
        inputs.setCurrentAge(35);
        inputs.setGoalAge(55);
        final GoalResult result = GoalCalculator.calculate(inputs);
        assertEquals(20, result.yearsToGoal());
        assertEquals(240, result.totalMonths());
        assertEquals(20, result.rows().size());
        final List<GoalProjectionRow> rows = result.rows();
        assertEquals(Integer.valueOf(36), rows.get(0).age());
        assertEquals(Integer.valueOf(55), rows.get(rows.size() - 1).age());
    }

    @Test
    void target_year_horizon_resolves_relative_to_now() {
        final int currentYear = Year.now().getValue();
        final int currentMonth = java.time.LocalDate.now().getMonthValue();
        final GoalInputs inputs = base(0);
        inputs.setHorizonMode(TimeHorizonMode.TARGET_YEAR);
        inputs.setYearsToGoal(null);
        inputs.setTargetYear(currentYear + 12);
        inputs.setTargetMonth(currentMonth);
        final GoalResult result = GoalCalculator.calculate(inputs);
        assertEquals(12 * 12, result.totalMonths());
        assertEquals(12, result.rows().size());
    }

    @Test
    void years_plus_months_horizon_emits_partial_final_row() {
        final GoalInputs inputs = base(20);
        inputs.setMonthsToGoal(6);
        final GoalResult result = GoalCalculator.calculate(inputs);
        assertEquals(20 * 12 + 6, result.totalMonths());
        // 20 full years + 1 partial (6-month) tail row.
        assertEquals(21, result.rows().size());
        final GoalProjectionRow finalRow = result.rows().get(20);
        assertEquals(6, finalRow.monthsInPeriod());
        // First 20 rows are whole years.
        for (int yearIndex = 0; yearIndex < 20; yearIndex++) {
            assertEquals(12, result.rows().get(yearIndex).monthsInPeriod(),
                    "year " + yearIndex + " should be a full 12-month row");
        }
    }

    @Test
    void target_month_in_past_within_same_year_rejected() {
        // Picking a month earlier than the current month in the current year
        // gives a non-positive horizon — calculator should reject.
        final int currentYear = Year.now().getValue();
        final int currentMonth = java.time.LocalDate.now().getMonthValue();
        if (currentMonth == 1) {
            return; // can't pick an earlier month in January
        }
        final GoalInputs inputs = base(0);
        inputs.setHorizonMode(TimeHorizonMode.TARGET_YEAR);
        inputs.setYearsToGoal(null);
        inputs.setTargetYear(currentYear);
        inputs.setTargetMonth(currentMonth - 1);
        assertThrows(IllegalArgumentException.class, () -> GoalCalculator.calculate(inputs));
    }

    @Test
    void short_horizon_emits_monthly_snapshots_for_chart() {
        final GoalInputs inputs = base(2);  // 24 months
        final GoalResult result = GoalCalculator.calculate(inputs);
        assertEquals(24, result.monthlySnapshots().size(),
                "horizon under 36 months should emit one snapshot per month");
        // First snapshot starts non-zero (one month of contribution + growth applied).
        assertTrue(result.monthlySnapshots().get(0).balance().signum() > 0);
        // Last monthly snapshot's balance matches the headline final balance.
        final var last = result.monthlySnapshots().get(23);
        assertEquals(0, last.balance().compareTo(result.finalBalance()));
    }

    @Test
    void long_horizon_skips_monthly_snapshots() {
        final GoalResult result = GoalCalculator.calculate(base(5));  // 60 months
        assertTrue(result.monthlySnapshots().isEmpty(),
                "horizons of 36+ months should not generate per-month chart data");
    }

    @Test
    void zero_horizon_rejected() {
        final GoalInputs inputs = base(0);
        inputs.setMonthsToGoal(0);
        assertThrows(IllegalArgumentException.class, () -> GoalCalculator.calculate(inputs));
    }

    @Test
    void negative_corpus_rejected() {
        final GoalInputs inputs = base(10);
        inputs.getInvestments().get(0).setCurrentCorpus(new BigDecimal("-1"));
        assertThrows(IllegalArgumentException.class, () -> GoalCalculator.calculate(inputs));
    }

    @Test
    void nonPositive_goal_rejected() {
        final GoalInputs inputs = base(10);
        inputs.setGoalAmount(BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> GoalCalculator.calculate(inputs));
    }

    @Test
    void hundredPercent_tax_still_solvable() {
        final GoalInputs inputs = base(15,
                BigDecimal.ZERO, BigDecimal.valueOf(12), new BigDecimal("100"));
        // With 100% tax, gains are zero net, but principal still counts — solvable.
        final GoalResult result = GoalCalculator.calculate(inputs);
        assertNotNull(result.monthlyInvestment());
        assertTrue(result.monthlyInvestment().signum() > 0);
    }
}
