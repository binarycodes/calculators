package io.binarycodes.calculators.buyrent.service;

import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.buyrent.domain.BuyRentInputs;
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
 * Guard rails for {@code buyrent-defaults.json}: it parses, every currency has
 * every required field, and the loaded defaults produce a valid projection.
 */
class BuyRentDefaultsJsonTest {

    private static final List<String> INTEGER_FIELDS = List.of("loanTermYears", "analysisYears");
    private static final List<String> DECIMAL_FIELDS = List.of(
            "homePrice", "downPaymentPct", "mortgageRatePct",
            "propertyTaxRatePct", "maintenancePct", "appreciationPct",
            "buyingCostPct", "sellingCostPct",
            "monthlyRent", "rentIncreasePct",
            "investmentReturnPct", "inflationRatePct",
            "propertyCapitalGainsTaxPct", "investmentGainsTaxPct");

    @Test
    void file_parses_as_valid_json() {
        assertDoesNotThrow(BuyRentDefaultsJsonTest::readTree);
    }

    @Test
    void each_supported_currency_has_an_entry() {
        final JsonNode root = readTree();
        for (final SupportedCurrency currency : SupportedCurrency.values()) {
            assertNotNull(root.get(currency.name()),
                    "buyrent-defaults.json missing entry for " + currency);
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
            for (final String field : DECIMAL_FIELDS) {
                final JsonNode value = requireField(currency, node, field);
                assertDoesNotThrow(() -> new BigDecimal(value.asString()),
                        currency + "." + field + " must parse as decimal");
            }
        }
    }

    @Test
    void provider_round_trips_to_calculator_without_throwing() throws Exception {
        final var provider = new BuyRentDefaultsProvider(
                new ClassPathResource("buyrent-defaults.json"));
        provider.load();
        for (final SupportedCurrency currency : SupportedCurrency.values()) {
            final BuyRentInputs inputs = provider.forCurrency(currency);
            assertNotNull(inputs, currency + ": defaults must be loaded");
            assertTrue(inputs.getHomePrice().signum() > 0,
                    currency + ": home price must be positive");
            assertTrue(inputs.getMonthlyRent().signum() > 0,
                    currency + ": monthly rent must be positive");
            assertDoesNotThrow(() -> BuyRentCalculator.calculate(inputs),
                    currency + ": defaults must produce a valid projection");
        }
    }

    private static JsonNode readTree() {
        try (InputStream stream = new ClassPathResource("buyrent-defaults.json").getInputStream()) {
            return JsonMapper.builder().build().readTree(stream);
        } catch (final Exception failure) {
            throw new AssertionError("buyrent-defaults.json failed to parse", failure);
        }
    }

    private static JsonNode requireField(SupportedCurrency currency, JsonNode node, String field) {
        final JsonNode value = node.get(field);
        assertNotNull(value, "buyrent-defaults.json[" + currency + "]." + field + " is missing");
        assertFalse(value.isNull(), "buyrent-defaults.json[" + currency + "]." + field + " is null");
        assertFalse(value.asString().isBlank(),
                "buyrent-defaults.json[" + currency + "]." + field + " is blank");
        return value;
    }
}
