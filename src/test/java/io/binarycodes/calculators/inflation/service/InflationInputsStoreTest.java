package io.binarycodes.calculators.inflation.service;

import io.binarycodes.calculators.base.common.TimeHorizonMode;
import io.binarycodes.calculators.inflation.domain.InflationInputs;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InflationInputsStoreTest {

    private final InflationInputsStore store = new InflationInputsStore();

    @Test
    void round_trip_preserves_all_fields() {
        final var inputs = new InflationInputs();
        inputs.setAmount(new BigDecimal("1000000"));
        inputs.setInflationRatePct(new BigDecimal("6"));
        inputs.setInflationVariationPct(new BigDecimal("2"));
        inputs.setAmountIsToday(true);
        inputs.setHorizonMode(TimeHorizonMode.AGES);
        inputs.setYearsToGoal(null);
        inputs.setMonthsToGoal(null);
        inputs.setCurrentAge(35);
        inputs.setGoalAge(60);
        inputs.setTargetYear(null);
        inputs.setTargetMonth(null);

        final ObjectNode json = store.toJsonNode(inputs);
        final InflationInputs restored = store.fromJsonNode(json);

        assertEquals(0, inputs.getAmount().compareTo(restored.getAmount()));
        assertEquals(0, inputs.getInflationRatePct().compareTo(restored.getInflationRatePct()));
        assertEquals(0, inputs.getInflationVariationPct().compareTo(restored.getInflationVariationPct()));
        assertTrue(restored.isAmountIsToday());
        assertEquals(TimeHorizonMode.AGES, restored.getHorizonMode());
        assertEquals(35, restored.getCurrentAge());
        assertEquals(60, restored.getGoalAge());
        assertNull(restored.getYearsToGoal());
        assertNull(restored.getTargetYear());
    }

    @Test
    void null_horizon_mode_defaults_to_years() {
        final var inputs = new InflationInputs();
        inputs.setHorizonMode(null);
        assertEquals(TimeHorizonMode.YEARS, store.fromJsonNode(store.toJsonNode(inputs)).getHorizonMode());
    }

    @Test
    void invalid_horizon_mode_string_falls_back_to_years() {
        final ObjectNode json = JsonMapper.builder().build().createObjectNode();
        json.put("horizonMode", "QUARTERLY"); // not a valid TimeHorizonMode value
        assertEquals(TimeHorizonMode.YEARS, store.fromJsonNode(json).getHorizonMode());
    }

    @Test
    void absent_bool_field_defaults_to_false() {
        final ObjectNode json = JsonMapper.builder().build().createObjectNode();
        assertFalse(store.fromJsonNode(json).isAmountIsToday());
    }
}
