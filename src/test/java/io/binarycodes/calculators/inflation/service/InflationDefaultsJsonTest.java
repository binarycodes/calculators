package io.binarycodes.calculators.inflation.service;

import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.inflation.domain.InflationInputs;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guard rails for {@code inflation-defaults.json}: it parses, every currency
 * has every required field, and the loaded defaults project without throwing.
 */
class InflationDefaultsJsonTest {

    private static final List<String> INTEGER_FIELDS = List.of(
            "yearsToGoal", "monthsToGoal", "currentAge", "goalAge", "targetYear", "targetMonth");

    private static final List<String> MONEY_FIELDS = List.of("amount");

    private static final List<String> PERCENTAGE_FIELDS = List.of("inflationRate");

    @Test
    void file_parses_as_valid_json() {
        assertDoesNotThrow(InflationDefaultsJsonTest::readTree);
    }

    @Test
    void each_supported_currency_has_an_entry() {
        final JsonNode root = readTree();
        for (final SupportedCurrency currency : SupportedCurrency.values()) {
            assertNotNull(root.get(currency.name()),
                    "inflation-defaults.json missing entry for " + currency);
        }
    }

    @Test
    void each_currency_has_every_required_field_with_parseable_value() {
        final JsonNode root = readTree();
        for (final SupportedCurrency currency : SupportedCurrency.values()) {
            final JsonNode node = root.get(currency.name());
            for (final String field : INTEGER_FIELDS) {
                final JsonNode value = requireField(currency, node, field);
                assertDoesNotThrow(() -> Integer.parseInt(value.asString()),
                        currency + "." + field + " must parse as int");
            }
            for (final String field : MONEY_FIELDS) {
                final JsonNode value = requireField(currency, node, field);
                assertDoesNotThrow(() -> new BigDecimal(value.asString()),
                        currency + "." + field + " must parse as decimal");
            }
            for (final String field : PERCENTAGE_FIELDS) {
                final JsonNode value = requireField(currency, node, field);
                assertDoesNotThrow(() -> new BigDecimal(value.asString()),
                        currency + "." + field + " must parse as decimal");
            }
        }
    }

    @Test
    void provider_round_trips_to_calculator_without_throwing() throws Exception {
        final var provider = new InflationDefaultsProvider(new ClassPathResource("inflation-defaults.json"));
        provider.load();
        for (final SupportedCurrency currency : SupportedCurrency.values()) {
            final InflationInputs inputs = provider.forCurrency(currency);
            assertNotNull(inputs, currency + ": defaults must be loaded");
            assertTrue(inputs.getAmount().signum() > 0, currency + ": amount must be positive");
            assertDoesNotThrow(() -> InflationCalculator.calculate(inputs),
                    currency + ": defaults must produce a valid projection");
        }
    }

    private static JsonNode readTree() {
        try (InputStream stream = new ClassPathResource("inflation-defaults.json").getInputStream()) {
            return JsonMapper.builder().build().readTree(stream);
        } catch (final Exception failure) {
            throw new AssertionError("inflation-defaults.json failed to parse", failure);
        }
    }

    private static JsonNode requireField(SupportedCurrency currency, JsonNode node, String field) {
        final JsonNode value = node.get(field);
        assertNotNull(value, "inflation-defaults.json[" + currency + "]." + field + " is missing");
        assertFalse(value.isNull(), "inflation-defaults.json[" + currency + "]." + field + " is null");
        assertFalse(value.asString().isBlank(),
                "inflation-defaults.json[" + currency + "]." + field + " is blank");
        return value;
    }
}
