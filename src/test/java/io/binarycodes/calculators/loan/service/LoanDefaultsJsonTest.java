package io.binarycodes.calculators.loan.service;

import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.loan.domain.LoanInputs;
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
 * Guard rails for {@code loan-defaults.json}: it parses, every currency has every
 * required field, and the loaded defaults produce a valid EMI schedule.
 */
class LoanDefaultsJsonTest {

    private static final List<String> INTEGER_FIELDS = List.of(
            "tenureYears", "tenureMonths", "extraEmisPerYear");
    private static final List<String> MONEY_FIELDS = List.of("loanAmount", "extraPerPeriod");
    private static final List<String> PERCENTAGE_FIELDS = List.of(
            "annualRate", "inflationRate", "emiStepUp");

    @Test
    void file_parses_as_valid_json() {
        assertDoesNotThrow(LoanDefaultsJsonTest::readTree);
    }

    @Test
    void each_supported_currency_has_an_entry() {
        final JsonNode root = readTree();
        for (final SupportedCurrency currency : SupportedCurrency.values()) {
            assertNotNull(root.get(currency.name()), "loan-defaults.json missing entry for " + currency);
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
            assertNotNull(node.get("extraFrequency"), currency + ": extraFrequency must be present");
        }
    }

    @Test
    void provider_round_trips_to_calculator_without_throwing() throws Exception {
        final var provider = new LoanDefaultsProvider(new ClassPathResource("loan-defaults.json"));
        provider.load();
        for (final SupportedCurrency currency : SupportedCurrency.values()) {
            final LoanInputs inputs = provider.forCurrency(currency);
            assertNotNull(inputs, currency + ": defaults must be loaded");
            assertTrue(inputs.getLoanAmount().signum() > 0, currency + ": loan amount must be positive");
            assertDoesNotThrow(() -> LoanCalculator.calculate(inputs),
                    currency + ": defaults must produce a valid schedule");
        }
    }

    private static JsonNode readTree() {
        try (InputStream stream = new ClassPathResource("loan-defaults.json").getInputStream()) {
            return JsonMapper.builder().build().readTree(stream);
        } catch (final Exception failure) {
            throw new AssertionError("loan-defaults.json failed to parse", failure);
        }
    }

    private static JsonNode requireField(SupportedCurrency currency, JsonNode node, String field) {
        final JsonNode value = node.get(field);
        assertNotNull(value, "loan-defaults.json[" + currency + "]." + field + " is missing");
        assertFalse(value.isNull(), "loan-defaults.json[" + currency + "]." + field + " is null");
        assertFalse(value.asString().isBlank(), "loan-defaults.json[" + currency + "]." + field + " is blank");
        return value;
    }
}
