package io.binarycodes.calculators.irr.service;

import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.irr.domain.XirrInputs;
import io.binarycodes.calculators.irr.domain.XirrResult;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guard rails for {@code xirr-defaults.json}: it parses, every currency has an
 * entry, and the loaded defaults resolve to a valid XIRR without throwing.
 */
class XirrDefaultsJsonTest {

    @Test
    void file_parses_as_valid_json() {
        assertDoesNotThrow(XirrDefaultsJsonTest::readTree);
    }

    @Test
    void each_supported_currency_has_an_entry() {
        final JsonNode root = readTree();
        for (final SupportedCurrency currency : SupportedCurrency.values()) {
            assertNotNull(root.get(currency.name()), "xirr-defaults.json missing entry for " + currency);
        }
    }

    @Test
    void provider_defaults_resolve_to_a_unique_positive_rate() throws Exception {
        final var provider = new XirrDefaultsProvider(new ClassPathResource("xirr-defaults.json"));
        provider.load();
        for (final SupportedCurrency currency : SupportedCurrency.values()) {
            final XirrInputs inputs = provider.forCurrency(currency);
            assertNotNull(inputs, currency + ": defaults must be loaded");
            final XirrResult result = assertDoesNotThrow(() -> XirrCalculator.calculate(inputs),
                    currency + ": defaults must produce a valid result");
            assertTrue(result.xirr().signum() > 0, currency + ": sample schedule should be profitable");
        }
    }

    @Test
    void forCurrency_returns_an_independent_copy() throws Exception {
        final var provider = new XirrDefaultsProvider(new ClassPathResource("xirr-defaults.json"));
        provider.load();
        final XirrInputs first = provider.forCurrency(SupportedCurrency.INR);
        first.getRecurringInvestments().clear();
        assertTrue(provider.forCurrency(SupportedCurrency.INR).getRecurringInvestments().size() > 0,
                "mutating one copy must not affect the cached default");
    }

    @Test
    void provider_falls_back_when_currency_entries_absent() throws Exception {
        final var provider = new XirrDefaultsProvider(new ByteArrayResource("{}".getBytes()));
        provider.load();
        assertNotNull(provider.forCurrency(SupportedCurrency.EUR));
    }

    private static JsonNode readTree() {
        try (InputStream stream = new ClassPathResource("xirr-defaults.json").getInputStream()) {
            return JsonMapper.builder().build().readTree(stream);
        } catch (final Exception failure) {
            throw new AssertionError("xirr-defaults.json failed to parse", failure);
        }
    }
}
