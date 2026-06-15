package io.binarycodes.calculators.base.common;

import io.binarycodes.calculators.base.money.SupportedCurrency;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ScenarioCodec} round-trips inputs + currency and rejects the abusive
 * tokens a shareable URL must defend against (oversized, malformed, wrong
 * version/currency, numerically absurd).
 */
class ScenarioCodecTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void round_trips_currency_and_inputs() {
        final ObjectNode inputs = MAPPER.createObjectNode();
        inputs.put("amount", "300000");
        inputs.put("growthRate", "12.5");
        inputs.put("label", "Retirement bucket");

        final String token = ScenarioCodec.encode(SupportedCurrency.EUR, inputs);
        final ScenarioCodec.Decoded decoded = ScenarioCodec.decode(token);

        assertEquals(SupportedCurrency.EUR, decoded.currency());
        assertEquals("300000", decoded.inputs().get("amount").asString());
        assertEquals("12.5", decoded.inputs().get("growthRate").asString());
        assertEquals("Retirement bucket", decoded.inputs().get("label").asString());
    }

    @Test
    void token_is_url_safe() {
        final ObjectNode inputs = MAPPER.createObjectNode();
        inputs.put("amount", "1000000");
        final String token = ScenarioCodec.encode(SupportedCurrency.USD, inputs);

        assertTrue(token.chars().noneMatch(ch -> ch == '+' || ch == '/' || ch == '='),
                "base64url token must not contain +, / or padding");
    }

    @Test
    void rejects_blank_token() {
        assertThrows(IllegalArgumentException.class, () -> ScenarioCodec.decode("   "));
    }

    @Test
    void rejects_garbage_token() {
        assertThrows(IllegalArgumentException.class, () -> ScenarioCodec.decode("not a real token"));
    }

    @Test
    void rejects_oversized_token() {
        final String huge = "A".repeat(9000);
        assertThrows(IllegalArgumentException.class, () -> ScenarioCodec.decode(huge));
    }

    @Test
    void rejects_unknown_currency() {
        final String token = encodeRaw("{\"v\":1,\"currency\":\"GBP\",\"inputs\":{}}");
        assertThrows(IllegalArgumentException.class, () -> ScenarioCodec.decode(token));
    }

    @Test
    void rejects_unsupported_version() {
        final String token = encodeRaw("{\"v\":99,\"currency\":\"INR\",\"inputs\":{}}");
        assertThrows(IllegalArgumentException.class, () -> ScenarioCodec.decode(token));
    }

    @Test
    void rejects_absurd_decimal_scale() {
        final String token = encodeRaw("{\"v\":1,\"currency\":\"INR\",\"inputs\":{\"amount\":\"1E999999999\"}}");
        assertThrows(IllegalArgumentException.class, () -> ScenarioCodec.decode(token));
    }

    @Test
    void rejects_absurd_decimal_in_nested_array() {
        final String token = encodeRaw(
                "{\"v\":1,\"currency\":\"INR\",\"inputs\":{\"investments\":[{\"currentCorpus\":\"1E999999999\"}]}}");
        assertThrows(IllegalArgumentException.class, () -> ScenarioCodec.decode(token));
    }

    private static String encodeRaw(String json) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
