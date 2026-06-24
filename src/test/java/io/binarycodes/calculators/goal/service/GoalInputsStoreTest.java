package io.binarycodes.calculators.goal.service;

import io.binarycodes.calculators.base.common.TimeHorizonMode;
import io.binarycodes.calculators.goal.domain.GoalInputs;
import io.binarycodes.calculators.goal.domain.Investment;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalInputsStoreTest {

    private final GoalInputsStore store = new GoalInputsStore();

    @Test
    void round_trip_preserves_scalar_fields_and_investment_list() {
        final var investment = new Investment();
        investment.setLabel("Equity MF");
        investment.setCurrentCorpus(new BigDecimal("500000"));
        investment.setGrowthRatePct(new BigDecimal("12"));
        investment.setWithdrawalTaxRatePct(new BigDecimal("10"));
        investment.setAllocationPct(new BigDecimal("100"));
        investment.setStepUpPct(new BigDecimal("5"));

        final var inputs = new GoalInputs();
        inputs.setGoalAmount(new BigDecimal("10000000"));
        inputs.setInflationRatePct(new BigDecimal("6"));
        inputs.setHorizonMode(TimeHorizonMode.AGES);
        inputs.setCurrentAge(35);
        inputs.setGoalAge(60);
        inputs.setYearsToGoal(null);
        inputs.setMonthsToGoal(null);
        inputs.setTargetYear(null);
        inputs.setTargetMonth(null);
        inputs.setInvestments(List.of(investment));

        final ObjectNode json = store.toJsonNode(inputs);
        final GoalInputs restored = store.fromJsonNode(json);

        assertEquals(0, inputs.getGoalAmount().compareTo(restored.getGoalAmount()));
        assertEquals(TimeHorizonMode.AGES, restored.getHorizonMode());
        assertEquals(35, restored.getCurrentAge());
        assertEquals(60, restored.getGoalAge());
        assertNull(restored.getYearsToGoal());

        assertEquals(1, restored.getInvestments().size());
        final Investment restoredInv = restored.getInvestments().getFirst();
        assertEquals("Equity MF", restoredInv.getLabel());
        assertEquals(0, investment.getCurrentCorpus().compareTo(restoredInv.getCurrentCorpus()));
        assertEquals(0, investment.getGrowthRatePct().compareTo(restoredInv.getGrowthRatePct()));
        assertEquals(0, investment.getWithdrawalTaxRatePct().compareTo(restoredInv.getWithdrawalTaxRatePct()));
        assertEquals(0, investment.getAllocationPct().compareTo(restoredInv.getAllocationPct()));
        assertEquals(0, investment.getStepUpPct().compareTo(restoredInv.getStepUpPct()));
    }

    @Test
    void null_investments_list_serializes_as_empty_array_and_restores_as_empty_list() {
        final var inputs = new GoalInputs();
        inputs.setInvestments(null);

        final ObjectNode json = store.toJsonNode(inputs);
        final GoalInputs restored = store.fromJsonNode(json);

        assertNotNull(restored.getInvestments());
        assertTrue(restored.getInvestments().isEmpty());
    }

    @Test
    void null_horizon_mode_defaults_to_years() {
        final var inputs = new GoalInputs();
        inputs.setHorizonMode(null);
        assertEquals(TimeHorizonMode.YEARS, store.fromJsonNode(store.toJsonNode(inputs)).getHorizonMode());
    }
}
