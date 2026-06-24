package io.binarycodes.calculators.investment.service;

import io.binarycodes.calculators.base.common.TimeHorizonMode;
import io.binarycodes.calculators.investment.domain.ContributionFrequency;
import io.binarycodes.calculators.investment.domain.InvestmentInputs;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InvestmentInputsStoreTest {

    private final InvestmentInputsStore store = new InvestmentInputsStore();

    @Test
    void round_trip_preserves_all_fields() {
        final var inputs = new InvestmentInputs();
        inputs.setAmount(new BigDecimal("10000"));
        inputs.setFrequency(ContributionFrequency.YEARLY);
        inputs.setGrowthRatePct(new BigDecimal("12"));
        inputs.setTaxRatePct(new BigDecimal("15"));
        inputs.setInflationRatePct(new BigDecimal("6"));
        inputs.setStepUpPct(new BigDecimal("10"));
        inputs.setHorizonMode(TimeHorizonMode.TARGET_YEAR);
        inputs.setInvestYears(null);
        inputs.setInvestMonths(null);
        inputs.setCurrentAge(null);
        inputs.setGoalAge(null);
        inputs.setTargetYear(2035);
        inputs.setTargetMonth(6);
        inputs.setHoldYears(5);
        inputs.setHoldMonths(3);

        final ObjectNode json = store.toJsonNode(inputs);
        final InvestmentInputs restored = store.fromJsonNode(json);

        assertEquals(0, inputs.getAmount().compareTo(restored.getAmount()));
        assertEquals(ContributionFrequency.YEARLY, restored.getFrequency());
        assertEquals(0, inputs.getGrowthRatePct().compareTo(restored.getGrowthRatePct()));
        assertEquals(0, inputs.getTaxRatePct().compareTo(restored.getTaxRatePct()));
        assertEquals(TimeHorizonMode.TARGET_YEAR, restored.getHorizonMode());
        assertEquals(2035, restored.getTargetYear());
        assertEquals(6, restored.getTargetMonth());
        assertEquals(5, restored.getHoldYears());
        assertEquals(3, restored.getHoldMonths());
        assertNull(restored.getInvestYears());
        assertNull(restored.getCurrentAge());
    }

    @Test
    void null_frequency_defaults_to_monthly() {
        final var inputs = new InvestmentInputs();
        inputs.setFrequency(null);
        assertEquals(ContributionFrequency.MONTHLY, store.fromJsonNode(store.toJsonNode(inputs)).getFrequency());
    }

    @Test
    void invalid_frequency_string_falls_back_to_monthly() {
        final ObjectNode json = JsonMapper.builder().build().createObjectNode();
        json.put("frequency", "WEEKLY"); // not a valid ContributionFrequency value
        json.put("horizonMode", "YEARS");
        assertEquals(ContributionFrequency.MONTHLY, store.fromJsonNode(json).getFrequency());
    }

    @Test
    void null_horizon_mode_defaults_to_years() {
        final var inputs = new InvestmentInputs();
        inputs.setHorizonMode(null);
        assertEquals(TimeHorizonMode.YEARS, store.fromJsonNode(store.toJsonNode(inputs)).getHorizonMode());
    }
}
