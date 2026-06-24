package io.binarycodes.calculators.retirement.service;

import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.retirement.domain.Frequency;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
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
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void provider_returns_fallback_when_all_currency_entries_absent() throws Exception {
        // Empty JSON → no currencies loaded → forCurrency falls through to fallback().
        final var provider = new DefaultsProvider(new ByteArrayResource("{}".getBytes()));
        provider.load();
        final RetirementInputs result = provider.forCurrency(SupportedCurrency.USD);
        assertNotNull(result);
        assertEquals(35, result.getCurrentAge());
        assertEquals(60, result.getRetireAge());
        assertEquals(90, result.getLifeExp());
        assertEquals(0, result.getCorpus().compareTo(BigDecimal.valueOf(5_000_000)));
    }

    @Test
    void all_array_fields_are_parsed_and_invalid_frequency_falls_back_to_monthly() throws Exception {
        // JSON with entries in all five array fields. "WEEKLY"/"QUARTERLY" are invalid
        // frequency values — readFrequency must return MONTHLY for both. A blank inflation
        // string in recurringExpenses must produce null via bdOrNull.
        // Together this exercises the loop bodies of all five read*Array methods.
        final String json = """
                {"INR":{
                  "currentAge":"35","retireAge":"60","lifeExp":"90",
                  "corpus":"5000000","monthlyExp":"50000","inflation":"6",
                  "growthPre":"12","growthPost":"8","corpusTaxRate":"0",
                  "monthlyInvPre":"10000","sipGrowthPre":"12","sipStepUpPre":"0","taxRatePre":"0",
                  "monthlyInvPost":"0","sipGrowthPost":"0","sipStepUpPost":"0","taxRatePost":"0",
                  "recurringExpenses":[{"year":2030,"stopYear":2035,"description":"EMI",
                    "amount":"5000","frequency":"WEEKLY","inflation":""}],
                  "recurringIncomes":[{"year":2031,"stopYear":2036,"description":"Rent",
                    "amount":"10000","frequency":"QUARTERLY","taxRate":"10"}],
                  "futureExpenses":[{"year":2040,"description":"Wedding",
                    "amount":"1000000","inflation":"5"}],
                  "retirementBenefits":[{"description":"Gratuity",
                    "amount":"500000","taxRate":"10"}],
                  "futureIncomes":[{"year":2042,"description":"Sale",
                    "amount":"2000000","taxRate":"15"}]
                }}
                """;
        final var provider = new DefaultsProvider(new ByteArrayResource(json.getBytes()));
        provider.load();
        final RetirementInputs inputs = provider.forCurrency(SupportedCurrency.INR);

        assertNotNull(inputs.getRecurringExpenses());
        assertEquals(1, inputs.getRecurringExpenses().size());
        assertEquals(Frequency.MONTHLY, inputs.getRecurringExpenses().get(0).getFrequency(),
                "WEEKLY must fall back to MONTHLY");
        assertNull(inputs.getRecurringExpenses().get(0).getInflationPct(),
                "blank inflation string must yield null from bdOrNull");

        assertNotNull(inputs.getRecurringIncomes());
        assertEquals(1, inputs.getRecurringIncomes().size());
        assertEquals(Frequency.MONTHLY, inputs.getRecurringIncomes().get(0).getFrequency(),
                "QUARTERLY must fall back to MONTHLY");

        assertNotNull(inputs.getFutureExpenses());
        assertEquals(1, inputs.getFutureExpenses().size());

        assertNotNull(inputs.getRetirementBenefits());
        assertEquals(1, inputs.getRetirementBenefits().size());

        assertNotNull(inputs.getFutureIncomes());
        assertEquals(1, inputs.getFutureIncomes().size());
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
