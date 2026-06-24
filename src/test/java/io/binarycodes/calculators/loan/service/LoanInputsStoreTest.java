package io.binarycodes.calculators.loan.service;

import io.binarycodes.calculators.loan.domain.LoanInputs;
import io.binarycodes.calculators.loan.domain.PrepaymentFrequency;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LoanInputsStoreTest {

    private final LoanInputsStore store = new LoanInputsStore();

    @Test
    void round_trip_preserves_all_fields() {
        final var inputs = new LoanInputs();
        inputs.setLoanAmount(new BigDecimal("2000000"));
        inputs.setAnnualRatePct(new BigDecimal("8.5"));
        inputs.setTenureYears(20);
        inputs.setTenureMonths(0);
        inputs.setInflationRatePct(new BigDecimal("6"));
        inputs.setExtraPerPeriod(new BigDecimal("5000"));
        inputs.setExtraFrequency(PrepaymentFrequency.MONTHLY);
        inputs.setExtraEmisPerYear(2);
        inputs.setEmiStepUpPct(new BigDecimal("10"));

        final ObjectNode json = store.toJsonNode(inputs);
        final LoanInputs restored = store.fromJsonNode(json);

        assertEquals(0, inputs.getLoanAmount().compareTo(restored.getLoanAmount()));
        assertEquals(0, inputs.getAnnualRatePct().compareTo(restored.getAnnualRatePct()));
        assertEquals(inputs.getTenureYears(), restored.getTenureYears());
        assertEquals(inputs.getTenureMonths(), restored.getTenureMonths());
        assertEquals(0, inputs.getInflationRatePct().compareTo(restored.getInflationRatePct()));
        assertEquals(0, inputs.getExtraPerPeriod().compareTo(restored.getExtraPerPeriod()));
        assertEquals(PrepaymentFrequency.MONTHLY, restored.getExtraFrequency());
        assertEquals(inputs.getExtraEmisPerYear(), restored.getExtraEmisPerYear());
        assertEquals(0, inputs.getEmiStepUpPct().compareTo(restored.getEmiStepUpPct()));
    }

    @Test
    void null_extra_frequency_defaults_to_yearly() {
        final var inputs = new LoanInputs();
        inputs.setLoanAmount(BigDecimal.valueOf(1_000_000));
        inputs.setAnnualRatePct(BigDecimal.valueOf(8));
        inputs.setExtraFrequency(null);

        final ObjectNode json = store.toJsonNode(inputs);
        assertEquals(PrepaymentFrequency.YEARLY, store.fromJsonNode(json).getExtraFrequency());
    }

    @Test
    void invalid_frequency_string_falls_back_to_yearly() {
        final ObjectNode json = JsonMapper.builder().build().createObjectNode();
        json.put("extraFrequency", "WEEKLY"); // not a valid PrepaymentFrequency value
        assertEquals(PrepaymentFrequency.YEARLY, store.fromJsonNode(json).getExtraFrequency());
    }

    @Test
    void missing_integer_fields_produce_null() {
        final ObjectNode json = JsonMapper.builder().build().createObjectNode();
        final LoanInputs restored = store.fromJsonNode(json);
        assertNull(restored.getTenureYears());
        assertNull(restored.getTenureMonths());
        assertNull(restored.getExtraEmisPerYear());
    }

    @Test
    void missing_decimal_fields_default_to_zero() {
        final ObjectNode json = JsonMapper.builder().build().createObjectNode();
        final LoanInputs restored = store.fromJsonNode(json);
        assertEquals(0, BigDecimal.ZERO.compareTo(restored.getLoanAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(restored.getAnnualRatePct()));
    }
}
