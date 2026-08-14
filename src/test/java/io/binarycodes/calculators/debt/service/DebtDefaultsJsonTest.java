package io.binarycodes.calculators.debt.service;

import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.debt.domain.Debt;
import io.binarycodes.calculators.debt.domain.DebtPlanInputs;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guard rails for {@code debt-defaults.json}: it parses, every currency has an
 * entry with a positive minimum floor and at least one well-formed debt, and the
 * loaded defaults feed the calculator to a real payoff plan without throwing.
 */
class DebtDefaultsJsonTest {

    @Test
    void file_parses_as_valid_json() {
        assertDoesNotThrow(DebtDefaultsJsonTest::readTree);
    }

    @Test
    void each_supported_currency_has_an_entry_with_a_floor_and_debts() {
        final JsonNode root = readTree();
        for (final SupportedCurrency currency : SupportedCurrency.values()) {
            final JsonNode node = root.get(currency.name());
            assertNotNull(node, "debt-defaults.json missing entry for " + currency);

            final JsonNode floor = node.get("minimumFloor");
            assertNotNull(floor, currency + ": minimumFloor is missing");
            assertTrue(new BigDecimal(floor.asString()).signum() > 0, currency + ": minimumFloor must be positive");

            final JsonNode debts = node.get("debts");
            assertNotNull(debts, currency + ": debts is missing");
            assertTrue(debts.isArray() && !debts.isEmpty(), currency + ": needs at least one debt");
            for (final JsonNode debt : debts) {
                assertFalse(debt.get("name").asString().isBlank(), currency + ": debt name is blank");
                assertTrue(new BigDecimal(debt.get("balance").asString()).signum() > 0,
                        currency + ": debt balance must be positive");
                assertDoesNotThrow(() -> new BigDecimal(debt.get("aprPct").asString()),
                        currency + ": aprPct must parse as decimal");
            }
        }
    }

    @Test
    void provider_feeds_the_calculator_without_throwing() throws Exception {
        final var provider = new DebtDefaultsProvider(new ClassPathResource("debt-defaults.json"));
        provider.load();
        for (final SupportedCurrency currency : SupportedCurrency.values()) {
            final DebtPlanInputs inputs = provider.forCurrency(currency);
            assertNotNull(inputs, currency + ": defaults must be loaded");
            assertFalse(inputs.getDebts().isEmpty(), currency + ": defaults must carry debts");
            for (final Debt debt : inputs.getDebts()) {
                assertTrue(debt.getBalance().signum() > 0, currency + ": debt balance must be positive");
            }
            assertTrue(provider.minimumFloor(currency).signum() > 0, currency + ": floor must be positive");
            assertTrue(inputs.getMonthlyBudget().signum() > 0, currency + ": defaults must carry a monthly budget");
            final var result = DebtCalculator.calculate(inputs, provider.minimumFloor(currency));
            assertTrue(result.primary().fullyPaid(),
                    currency + ": the default budget should clear the debts within the cap");
        }
    }

    private static JsonNode readTree() {
        try (InputStream stream = new ClassPathResource("debt-defaults.json").getInputStream()) {
            return JsonMapper.builder().build().readTree(stream);
        } catch (final Exception failure) {
            throw new AssertionError("debt-defaults.json failed to parse", failure);
        }
    }
}
