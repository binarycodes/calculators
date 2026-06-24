package io.binarycodes.calculators.buyrent.service;

import io.binarycodes.calculators.buyrent.domain.BuyRentInputs;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BuyRentInputsStoreTest {

    private final BuyRentInputsStore store = new BuyRentInputsStore();

    @Test
    void round_trip_preserves_all_fields() {
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
        inputs.setPropertyCapitalGainsTaxPct(new BigDecimal("20"));
        inputs.setInvestmentGainsTaxPct(new BigDecimal("15"));

        final ObjectNode json = store.toJsonNode(inputs);
        final BuyRentInputs restored = store.fromJsonNode(json);

        assertEquals(0, inputs.getHomePrice().compareTo(restored.getHomePrice()));
        assertEquals(0, inputs.getDownPaymentPct().compareTo(restored.getDownPaymentPct()));
        assertEquals(inputs.getLoanTermYears(), restored.getLoanTermYears());
        assertEquals(0, inputs.getMortgageRatePct().compareTo(restored.getMortgageRatePct()));
        assertEquals(0, inputs.getPropertyTaxRatePct().compareTo(restored.getPropertyTaxRatePct()));
        assertEquals(0, inputs.getMaintenancePct().compareTo(restored.getMaintenancePct()));
        assertEquals(0, inputs.getAppreciationPct().compareTo(restored.getAppreciationPct()));
        assertEquals(0, inputs.getBuyingCostPct().compareTo(restored.getBuyingCostPct()));
        assertEquals(0, inputs.getSellingCostPct().compareTo(restored.getSellingCostPct()));
        assertEquals(0, inputs.getMonthlyRent().compareTo(restored.getMonthlyRent()));
        assertEquals(0, inputs.getRentIncreasePct().compareTo(restored.getRentIncreasePct()));
        assertEquals(0, inputs.getInvestmentReturnPct().compareTo(restored.getInvestmentReturnPct()));
        assertEquals(0, inputs.getInflationRatePct().compareTo(restored.getInflationRatePct()));
        assertEquals(inputs.getAnalysisYears(), restored.getAnalysisYears());
        assertEquals(0, inputs.getPropertyCapitalGainsTaxPct().compareTo(restored.getPropertyCapitalGainsTaxPct()));
        assertEquals(0, inputs.getInvestmentGainsTaxPct().compareTo(restored.getInvestmentGainsTaxPct()));
    }

    @Test
    void null_bigdecimal_fields_survive_round_trip_as_null() {
        // BuyRentInputsStore.bd() returns null (not ZERO) when field is absent — different from other stores.
        final var inputs = new BuyRentInputs(); // all fields null
        final ObjectNode json = store.toJsonNode(inputs);
        final BuyRentInputs restored = store.fromJsonNode(json);

        assertNull(restored.getHomePrice());
        assertNull(restored.getMonthlyRent());
    }

    @Test
    void missing_integer_fields_produce_null() {
        final ObjectNode json = JsonMapper.builder().build().createObjectNode();
        final BuyRentInputs restored = store.fromJsonNode(json);

        assertNull(restored.getLoanTermYears());
        assertNull(restored.getAnalysisYears());
    }
}
