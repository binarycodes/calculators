package io.binarycodes.calculators.base.common;

import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.irr.service.XirrInputsStore;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;
import io.binarycodes.calculators.retirement.service.RetirementInputsStore;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ScenarioCodec} validates the envelope but not the shape of {@code inputs},
 * so a token can be perfectly well-formed and still make a store's
 * {@code fromJsonNode} blow up. These cases pin that every such failure surfaces as
 * an empty {@link Optional} — the "Invalid share link" path — rather than escaping
 * as an unchecked exception that would reach Vaadin's internal-error overlay.
 */
class SharedScenarioTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static String tokenWithInputs(ObjectNode inputs) {
        return ScenarioCodec.encode(SupportedCurrency.INR, inputs);
    }

    @Test
    void valid_token_round_trips_into_currency_and_inputs() {
        final var store = new RetirementInputsStore();
        final var inputs = new RetirementInputs();
        inputs.setCurrentAge(35);
        inputs.setRetireAge(60);
        inputs.setLifeExp(85);
        inputs.setCorpus(new BigDecimal("2500000"));

        final Optional<SharedScenario<RetirementInputs>> parsed =
                SharedScenario.parse(ScenarioCodec.encode(SupportedCurrency.USD, store.toJsonNode(inputs)), store);

        assertTrue(parsed.isPresent());
        assertEquals(SupportedCurrency.USD, parsed.get().currency());
        assertEquals(35, parsed.get().inputs().getCurrentAge());
        assertEquals(60, parsed.get().inputs().getRetireAge());
    }

    @Test
    void empty_inputs_object_is_rejected_instead_of_throwing() {
        final Optional<SharedScenario<RetirementInputs>> parsed =
                SharedScenario.parse(tokenWithInputs(MAPPER.createObjectNode()), new RetirementInputsStore());

        assertTrue(parsed.isEmpty(), "a token missing every field must not throw out of parse");
    }

    @Test
    void unparseable_date_is_rejected_instead_of_throwing() {
        final ObjectNode cashflow = MAPPER.createObjectNode();
        cashflow.put("date", "not-a-date");
        cashflow.put("description", "Lump sum");
        cashflow.put("amount", "50000");
        final ObjectNode inputs = MAPPER.createObjectNode();
        inputs.putArray("oneOffInvestments").add(cashflow);

        assertTrue(SharedScenario.parse(tokenWithInputs(inputs), new XirrInputsStore()).isEmpty());
    }

    @Test
    void unparseable_integer_is_rejected_instead_of_throwing() {
        final ObjectNode recurring = MAPPER.createObjectNode();
        recurring.put("startDate", "2021-01-01");
        recurring.put("frequency", "QUARTERLY");
        recurring.put("count", "not-a-number");
        recurring.put("description", "Premium");
        recurring.put("amount", "10000");
        final ObjectNode inputs = MAPPER.createObjectNode();
        inputs.putArray("recurringInvestments").add(recurring);

        assertTrue(SharedScenario.parse(tokenWithInputs(inputs), new XirrInputsStore()).isEmpty());
    }

    @Test
    void malformed_envelope_is_rejected() {
        assertTrue(SharedScenario.parse("not-a-token", new RetirementInputsStore()).isEmpty());
    }
}
