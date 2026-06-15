package io.binarycodes.calculators.goal.service;

import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.goal.domain.GoalInputs;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guard rails for {@code goal-defaults.json} mirroring {@code DefaultsJsonTest}.
 * Catches syntax errors and missing or unparseable fields per currency before
 * they reach the runtime as silent zeros.
 */
class GoalDefaultsJsonTest {

    private static final List<String> INTEGER_FIELDS = List.of(
            "yearsToGoal", "monthsToGoal", "currentAge", "goalAge", "targetYear", "targetMonth");

    private static final List<String> MONEY_FIELDS = List.of("goalAmount");

    private static final List<String> PERCENTAGE_FIELDS = List.of();

    @Test
    void file_parses_as_valid_json() {
        assertDoesNotThrow(GoalDefaultsJsonTest::readTree,
                "goal-defaults.json must be syntactically valid JSON");
    }

    @Test
    void each_supported_currency_has_an_entry() {
        final JsonNode root = readTree();
        for (final SupportedCurrency currency : SupportedCurrency.values()) {
            assertNotNull(root.get(currency.name()),
                    "goal-defaults.json missing entry for " + currency);
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
    void provider_loads_and_returns_sensible_values_for_every_currency() throws Exception {
        final var provider = new GoalDefaultsProvider(new ClassPathResource("goal-defaults.json"));
        provider.load();
        for (final SupportedCurrency currency : SupportedCurrency.values()) {
            final GoalInputs inputs = provider.forCurrency(currency);
            assertNotNull(inputs, currency + ": defaults must be loaded");
            assertTrue(inputs.getYearsToGoal() >= 1,
                    currency + ": yearsToGoal must be at least one");
            assertTrue(inputs.getGoalAmount().signum() > 0,
                    currency + ": goalAmount must be positive");
            assertTrue(inputs.getCurrentAge() < inputs.getGoalAge(),
                    currency + ": currentAge must be less than goalAge");
            assertTrue(inputs.getInvestments() != null && !inputs.getInvestments().isEmpty(),
                    currency + ": defaults must include at least one investment");
            java.math.BigDecimal allocationSum = java.math.BigDecimal.ZERO;
            for (final var investment : inputs.getInvestments()) {
                allocationSum = allocationSum.add(investment.getAllocationPct());
            }
            assertEquals(0, allocationSum.compareTo(java.math.BigDecimal.valueOf(100)),
                    currency + ": investment allocations must sum to 100");
        }
    }

    @Test
    void provider_round_trips_to_calculator_without_throwing() throws Exception {
        final var provider = new GoalDefaultsProvider(new ClassPathResource("goal-defaults.json"));
        provider.load();
        for (final SupportedCurrency currency : SupportedCurrency.values()) {
            final GoalInputs inputs = provider.forCurrency(currency);
            assertDoesNotThrow(() -> GoalCalculator.calculate(inputs),
                    currency + ": defaults must produce a valid projection");
        }
    }

    private static JsonNode readTree() {
        try (InputStream stream = new ClassPathResource("goal-defaults.json").getInputStream()) {
            return JsonMapper.builder().build().readTree(stream);
        } catch (final Exception failure) {
            throw new AssertionError("goal-defaults.json failed to parse", failure);
        }
    }

    private static JsonNode requireField(SupportedCurrency currency, JsonNode node, String field) {
        final JsonNode value = node.get(field);
        assertNotNull(value, "goal-defaults.json[" + currency + "]." + field + " is missing");
        assertFalse(value.isNull(), "goal-defaults.json[" + currency + "]." + field + " is null");
        assertFalse(value.asString().isBlank(),
                "goal-defaults.json[" + currency + "]." + field + " is blank");
        return value;
    }
}
