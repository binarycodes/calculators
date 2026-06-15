package io.binarycodes.calculators.retirement.service;

import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;
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
 * Guard rails for {@code defaults.json}. Catches syntax errors (missing
 * commas, stray characters) and missing or unparseable fields per currency
 * before they reach the runtime as silent zeros.
 */
class DefaultsJsonTest {

    private static final List<String> INTEGER_FIELDS = List.of(
            "currentAge", "retireAge", "lifeExp");

    private static final List<String> MONEY_FIELDS = List.of(
            "corpus", "monthlyExp", "monthlyInvPre", "monthlyInvPost");

    private static final List<String> PERCENTAGE_FIELDS = List.of(
            "inflation",
            "growthPre", "growthPost", "corpusTaxRate",
            "sipGrowthPre", "sipStepUpPre", "taxRatePre",
            "sipGrowthPost", "sipStepUpPost", "taxRatePost");

    @Test
    void file_parses_as_valid_json() {
        assertDoesNotThrow(DefaultsJsonTest::readTree,
                "defaults.json must be syntactically valid JSON");
    }

    @Test
    void each_supported_currency_has_an_entry() {
        final JsonNode root = readTree();
        for (final SupportedCurrency currency : SupportedCurrency.values()) {
            assertNotNull(root.get(currency.name()),
                    "defaults.json missing entry for " + currency);
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
                        currency + "." + field + " must parse as int (got '" + value.asString() + "')");
            }
            for (final String field : MONEY_FIELDS) {
                final JsonNode value = requireField(currency, node, field);
                assertDoesNotThrow(() -> new BigDecimal(value.asString()),
                        currency + "." + field + " must parse as decimal (got '" + value.asString() + "')");
            }
            for (final String field : PERCENTAGE_FIELDS) {
                final JsonNode value = requireField(currency, node, field);
                assertDoesNotThrow(() -> new BigDecimal(value.asString()),
                        currency + "." + field + " must parse as decimal (got '" + value.asString() + "')");
            }
        }
    }

    @Test
    void provider_loads_and_returns_sensible_ages_for_every_currency() throws Exception {
        final var provider = new DefaultsProvider(new ClassPathResource("defaults.json"));
        provider.load();
        for (final SupportedCurrency currency : SupportedCurrency.values()) {
            final RetirementInputs inputs = provider.forCurrency(currency);
            assertNotNull(inputs, currency + ": defaults must be loaded");
            assertTrue(inputs.getCurrentAge() < inputs.getRetireAge(),
                    currency + ": currentAge must be less than retireAge");
            assertTrue(inputs.getRetireAge() < inputs.getLifeExp(),
                    currency + ": retireAge must be less than lifeExp");
            assertTrue(inputs.getCorpus().signum() >= 0,
                    currency + ": corpus must be non-negative");
            assertTrue(inputs.getMonthlyExpenses().signum() > 0,
                    currency + ": monthlyExp must be positive");
        }
    }

    @Test
    void provider_round_trips_to_calculator_without_throwing() throws Exception {
        final var provider = new DefaultsProvider(new ClassPathResource("defaults.json"));
        provider.load();
        for (final SupportedCurrency currency : SupportedCurrency.values()) {
            final RetirementInputs inputs = provider.forCurrency(currency);
            // A full projection run is the strongest end-to-end sanity check —
            // anything missing/null/zero that the calculator can't tolerate will
            // surface here as a NPE or arithmetic exception.
            assertDoesNotThrow(() -> RetirementCalculator.calculate(inputs),
                    currency + ": defaults must produce a valid projection");
        }
    }

    private static JsonNode readTree() {
        try (InputStream in = new ClassPathResource("defaults.json").getInputStream()) {
            return JsonMapper.builder().build().readTree(in);
        } catch (final Exception e) {
            throw new AssertionError("defaults.json failed to parse", e);
        }
    }

    private static JsonNode requireField(SupportedCurrency currency, JsonNode node, String field) {
        final JsonNode value = node.get(field);
        assertNotNull(value, "defaults.json[" + currency + "]." + field + " is missing");
        assertFalse(value.isNull(), "defaults.json[" + currency + "]." + field + " is null");
        assertFalse(value.asString().isBlank(), "defaults.json[" + currency + "]." + field + " is blank");
        return value;
    }
}
