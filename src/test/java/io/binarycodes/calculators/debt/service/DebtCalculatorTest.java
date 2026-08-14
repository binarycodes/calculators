package io.binarycodes.calculators.debt.service;

import io.binarycodes.calculators.debt.domain.Debt;
import io.binarycodes.calculators.debt.domain.DebtPlanInputs;
import io.binarycodes.calculators.debt.domain.DebtPlanResult;
import io.binarycodes.calculators.debt.domain.DebtScheduleResult;
import io.binarycodes.calculators.debt.domain.PayoffStrategy;
import io.binarycodes.calculators.debt.domain.Windfall;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebtCalculatorTest {

    private static final BigDecimal FLOOR = new BigDecimal("25");

    private static Debt debt(String name, String balance, String aprPct, String minPct) {
        final Debt debt = new Debt();
        debt.setName(name);
        debt.setBalance(new BigDecimal(balance));
        debt.setAprPct(new BigDecimal(aprPct));
        if (minPct != null) {
            debt.setMinimumPct(new BigDecimal(minPct));
        }
        return debt;
    }

    private static DebtPlanInputs inputs(PayoffStrategy strategy, String extraPerMonth, Debt... debts) {
        final DebtPlanInputs inputs = new DebtPlanInputs();
        inputs.setDebts(new ArrayList<>(List.of(debts)));
        inputs.setStrategy(strategy);
        inputs.setExtraPerMonth(new BigDecimal(extraPerMonth));
        return inputs;
    }

    private static int payoff(DebtPlanResult result, String debtName) {
        return result.primary().payoffMonthByDebt().get(debtName);
    }

    @Test
    void avalanche_targets_the_highest_apr_first() {
        // Smaller balance carries the lower rate, so avalanche and snowball disagree.
        final DebtPlanInputs inputs = inputs(PayoffStrategy.AVALANCHE, "500",
                debt("small-cheap", "500", "10", "5"),
                debt("large-costly", "2000", "30", "5"));
        final DebtPlanResult result = DebtCalculator.calculate(inputs, FLOOR);
        assertTrue(payoff(result, "large-costly") < payoff(result, "small-cheap"),
                "avalanche should clear the 30% debt before the 10% debt");
    }

    @Test
    void snowball_targets_the_smallest_balance_first() {
        final DebtPlanInputs inputs = inputs(PayoffStrategy.SNOWBALL, "500",
                debt("small-cheap", "500", "10", "5"),
                debt("large-costly", "2000", "30", "5"));
        final DebtPlanResult result = DebtCalculator.calculate(inputs, FLOOR);
        assertTrue(payoff(result, "small-cheap") < payoff(result, "large-costly"),
                "snowball should clear the smallest balance first");
    }

    @Test
    void same_month_cascade_retires_multiple_debts_in_one_month() {
        // A big surplus with no interest clears both tiny debts in month one.
        final DebtPlanInputs inputs = inputs(PayoffStrategy.AVALANCHE, "1000",
                debt("a", "100", "0", null),
                debt("b", "100", "0", null));
        final DebtPlanResult result = DebtCalculator.calculate(inputs, FLOOR);
        assertEquals(1, payoff(result, "a"));
        assertEquals(1, payoff(result, "b"));
    }

    @Test
    void extra_payment_saves_interest_and_months_against_the_baseline() {
        final DebtPlanInputs inputs = inputs(PayoffStrategy.AVALANCHE, "500",
                debt("small-cheap", "500", "10", "5"),
                debt("large-costly", "2000", "30", "5"));
        final DebtPlanResult result = DebtCalculator.calculate(inputs, FLOOR);
        assertTrue(result.interestSaved().signum() > 0, "the extra payment should save interest");
        assertTrue(result.monthsSaved() > 0, "the extra payment should shorten the payoff");
    }

    @Test
    void a_promo_window_debt_with_a_high_ongoing_rate_is_still_prioritised() {
        // 'promo' is 0% now but 25% after the window; avalanche must rank it by the
        // ongoing 25%, ahead of the steady 20% debt, from month one.
        final Debt promo = debt("promo", "1000", "25", "5");
        promo.setPromoAprPct(BigDecimal.ZERO);
        promo.setPromoMonths(12);
        final DebtPlanInputs inputs = inputs(PayoffStrategy.AVALANCHE, "400",
                promo, debt("steady", "1000", "20", "5"));
        final DebtPlanResult result = DebtCalculator.calculate(inputs, FLOOR);
        assertTrue(payoff(result, "promo") < payoff(result, "steady"),
                "the promo debt is prioritised by its post-promo rate");
    }

    @Test
    void promo_rate_only_applies_within_the_window() {
        final Debt withPromo = debt("card", "5000", "24", "5");
        withPromo.setPromoAprPct(BigDecimal.ZERO);
        withPromo.setPromoMonths(6);
        final BigDecimal promoInterest = DebtCalculator
                .calculate(inputs(PayoffStrategy.AVALANCHE, "200", withPromo), FLOOR)
                .primary().totalInterest();

        final BigDecimal fullInterest = DebtCalculator
                .calculate(inputs(PayoffStrategy.AVALANCHE, "200", debt("card", "5000", "24", "5")), FLOOR)
                .primary().totalInterest();

        assertTrue(promoInterest.compareTo(fullInterest) < 0,
                "the promo window should reduce total interest versus the full rate");
    }

    @Test
    void percentage_minimum_shrinks_so_it_pays_off_slower_than_a_fixed_minimum() {
        final Debt percentMinimum = debt("pct", "10000", "12", "5");
        final Debt fixedMinimum = debt("fixed", "10000", "12", null);
        fixedMinimum.setMinimumPayment(new BigDecimal("500")); // 5% of the initial balance, held flat

        final int percentPayoff = DebtCalculator
                .calculate(inputs(PayoffStrategy.AVALANCHE, "0", percentMinimum), FLOOR)
                .baseline().payoffMonth();
        final int fixedPayoff = DebtCalculator
                .calculate(inputs(PayoffStrategy.AVALANCHE, "0", fixedMinimum), FLOOR)
                .baseline().payoffMonth();

        assertTrue(percentPayoff > fixedPayoff,
                "a shrinking percentage minimum should take longer than a flat minimum");
    }

    @Test
    void a_percentage_only_minimum_still_clears_via_the_floor() {
        final DebtPlanResult result = DebtCalculator.calculate(
                inputs(PayoffStrategy.AVALANCHE, "0", debt("card", "8000", "18", "5")), FLOOR);
        assertTrue(result.baseline().payoffMonth() > 0, "the debt should reach a finite payoff");
    }

    @Test
    void an_infeasible_budget_is_flagged() {
        // 100% APR on a large balance with only the tiny floor to pay it — the total
        // balance can never fall.
        final IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                DebtCalculator.calculate(
                        inputs(PayoffStrategy.AVALANCHE, "0", debt("runaway", "100000", "100", null)), FLOOR));
        assertEquals("debt.warning.infeasible", error.getMessage());
    }

    @Test
    void no_debts_is_rejected() {
        final IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                DebtCalculator.calculate(inputs(PayoffStrategy.AVALANCHE, "100"), FLOOR));
        assertEquals("debt.validation.needDebt", error.getMessage());
    }

    @Test
    void a_single_debt_makes_both_strategies_identical() {
        final DebtPlanResult result = DebtCalculator.calculate(
                inputs(PayoffStrategy.AVALANCHE, "300", debt("only", "6000", "15", "5")), FLOOR);
        assertEquals(result.avalanche().payoffMonth(), result.snowball().payoffMonth());
        assertEquals(result.avalanche().totalInterest(), result.snowball().totalInterest());
    }

    @Test
    void zero_apr_accrues_no_interest_and_pays_off_on_schedule() {
        // ₹1200 with a ₹25 floor + ₹175 extra = ₹200/month → six months, no interest.
        final DebtPlanResult result = DebtCalculator.calculate(
                inputs(PayoffStrategy.AVALANCHE, "175", debt("free", "1200", "0", null)), FLOOR);
        assertEquals(0, result.primary().totalInterest().signum());
        assertEquals(6, result.primary().payoffMonth());
    }


    /** A fixed-minimum debt so the payoff spans several years and the annual step-up compounds. */
    private static Debt fixedMinimumDebt() {
        final Debt debt = debt("loan", "400000", "18", null);
        debt.setMinimumPayment(new BigDecimal("7000"));
        return debt;
    }

    @Test
    void an_annual_step_up_shortens_the_payoff() {
        final DebtPlanInputs flat = inputs(PayoffStrategy.AVALANCHE, "1500", fixedMinimumDebt());
        final int flatPayoff = DebtCalculator.calculate(flat, FLOOR).primary().payoffMonth();

        final DebtPlanInputs stepped = inputs(PayoffStrategy.AVALANCHE, "1500", fixedMinimumDebt());
        stepped.setExtraStepUpPct(new BigDecimal("50"));
        final int steppedPayoff = DebtCalculator.calculate(stepped, FLOOR).primary().payoffMonth();

        assertTrue(steppedPayoff < flatPayoff, "growing the extra each year should pay off sooner");
    }

    @Test
    void a_zero_step_up_reproduces_the_flat_plan() {
        final DebtPlanInputs flat = inputs(PayoffStrategy.AVALANCHE, "1500", fixedMinimumDebt());
        final DebtPlanInputs zeroStep = inputs(PayoffStrategy.AVALANCHE, "1500", fixedMinimumDebt());
        zeroStep.setExtraStepUpPct(BigDecimal.ZERO);

        assertEquals(DebtCalculator.calculate(flat, FLOOR).primary().payoffMonth(),
                DebtCalculator.calculate(zeroStep, FLOOR).primary().payoffMonth());
    }


    @Test
    void a_windfall_cuts_interest_and_shortens_the_payoff() {
        final DebtPlanInputs plain = inputs(PayoffStrategy.AVALANCHE, "2000", debt("card", "300000", "30", "5"));
        final DebtPlanResult without = DebtCalculator.calculate(plain, FLOOR);

        final DebtPlanInputs withWindfall = inputs(PayoffStrategy.AVALANCHE, "2000", debt("card", "300000", "30", "5"));
        withWindfall.setWindfalls(new ArrayList<>(List.of(new Windfall(3, new BigDecimal("100000")))));
        final DebtPlanResult with = DebtCalculator.calculate(withWindfall, FLOOR);

        assertTrue(with.primary().payoffMonth() < without.primary().payoffMonth(),
                "a windfall should shorten the payoff");
        assertTrue(with.primary().totalInterest().compareTo(without.primary().totalInterest()) < 0,
                "a windfall should cut total interest");
    }

    @Test
    void a_windfall_follows_the_strategy_order() {
        // Avalanche targets the 30% debt; the windfall must accelerate that one.
        final DebtPlanInputs base = inputs(PayoffStrategy.AVALANCHE, "500",
                debt("cheap", "500", "10", "5"), debt("costly", "2000", "30", "5"));
        final int costlyWithout = DebtCalculator.calculate(base, FLOOR).primary().payoffMonthByDebt().get("costly");

        final DebtPlanInputs withWindfall = inputs(PayoffStrategy.AVALANCHE, "500",
                debt("cheap", "500", "10", "5"), debt("costly", "2000", "30", "5"));
        withWindfall.setWindfalls(new ArrayList<>(List.of(new Windfall(2, new BigDecimal("800")))));
        final int costlyWith = DebtCalculator.calculate(withWindfall, FLOOR).primary().payoffMonthByDebt().get("costly");

        assertTrue(costlyWith < costlyWithout, "the windfall should land on the avalanche target");
    }

    @Test
    void a_large_windfall_cascades_to_clear_multiple_debts_in_its_month() {
        final DebtPlanInputs inputs = inputs(PayoffStrategy.AVALANCHE, "0",
                debt("a", "100", "0", null), debt("b", "100", "0", null));
        inputs.setWindfalls(new ArrayList<>(List.of(new Windfall(1, new BigDecimal("1000")))));
        final DebtPlanResult result = DebtCalculator.calculate(inputs, FLOOR);
        assertEquals(1, result.primary().payoffMonthByDebt().get("a"));
        assertEquals(1, result.primary().payoffMonthByDebt().get("b"));
    }

    @Test
    void a_windfall_after_payoff_is_a_no_op() {
        final DebtPlanInputs plain = inputs(PayoffStrategy.AVALANCHE, "175", debt("free", "1200", "0", null));
        final DebtPlanResult without = DebtCalculator.calculate(plain, FLOOR);

        final DebtPlanInputs late = inputs(PayoffStrategy.AVALANCHE, "175", debt("free", "1200", "0", null));
        late.setWindfalls(new ArrayList<>(List.of(new Windfall(50, new BigDecimal("100000")))));
        final DebtPlanResult with = DebtCalculator.calculate(late, FLOOR);

        assertEquals(without.primary().payoffMonth(), with.primary().payoffMonth());
        assertEquals(0, without.primary().totalInterest().compareTo(with.primary().totalInterest()));
    }


    @Test
    void custom_order_funnels_in_input_order_distinct_from_avalanche_and_snowball() {
        // Input order X, Y, Z; each strategy's head differs: custom→X, snowball→Y
        // (smallest balance), avalanche→Z (highest rate).
        final DebtPlanResult result = DebtCalculator.calculate(inputs(PayoffStrategy.CUSTOM, "500",
                debt("X", "1000", "10", "5"),
                debt("Y", "500", "15", "5"),
                debt("Z", "1500", "30", "5")), FLOOR);

        assertEquals("X", firstCleared(result.primary()));
        assertEquals("Z", firstCleared(result.avalanche()));
        assertEquals("Y", firstCleared(result.snowball()));
    }

    @Test
    void reordering_the_debts_changes_the_custom_run() {
        final DebtPlanResult reordered = DebtCalculator.calculate(inputs(PayoffStrategy.CUSTOM, "500",
                debt("Z", "1500", "30", "5"),
                debt("Y", "500", "15", "5"),
                debt("X", "1000", "10", "5")), FLOOR);
        assertEquals("Z", firstCleared(reordered.primary()));
    }

    private static String firstCleared(DebtScheduleResult schedule) {
        return schedule.payoffMonthByDebt().entrySet().stream()
                .min(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElseThrow();
    }
}
