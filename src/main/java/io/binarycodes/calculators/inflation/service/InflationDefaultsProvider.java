package io.binarycodes.calculators.inflation.service;

import io.binarycodes.calculators.base.common.CalculatorDefaults;
import io.binarycodes.calculators.base.common.TimeHorizonMode;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.inflation.domain.InflationInputs;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * Loads {@code inflation-defaults.json} from classpath into typed
 * {@link InflationInputs} per currency.
 */
@Service
public class InflationDefaultsProvider implements CalculatorDefaults<InflationInputs> {

    private final Resource defaultsResource;
    private final Map<SupportedCurrency, InflationInputs> defaults = new EnumMap<>(SupportedCurrency.class);

    public InflationDefaultsProvider(@Value("classpath:inflation-defaults.json") Resource defaultsResource) {
        this.defaultsResource = defaultsResource;
    }

    @PostConstruct
    void load() throws IOException {
        final ObjectMapper objectMapper = JsonMapper.builder().build();
        try (InputStream stream = this.defaultsResource.getInputStream()) {
            final JsonNode root = objectMapper.readTree(stream);
            for (final SupportedCurrency currency : SupportedCurrency.values()) {
                final JsonNode node = root.get(currency.name());
                if (node != null) {
                    this.defaults.put(currency, toInputs(node));
                }
            }
        }
    }

    public InflationInputs forCurrency(SupportedCurrency currency) {
        final InflationInputs inputs = this.defaults.get(currency);
        if (inputs != null) {
            return copy(inputs);
        }
        final InflationInputs inrDefault = this.defaults.get(SupportedCurrency.INR);
        return inrDefault == null ? fallback() : copy(inrDefault);
    }

    private static InflationInputs toInputs(JsonNode node) {
        final var inputs = new InflationInputs();
        inputs.setAmount(bd(node, "amount"));
        inputs.setInflationRatePct(bd(node, "inflationRate"));
        inputs.setInflationVariationPct(bd(node, "inflationVariation"));
        inputs.setAmountIsToday(boolField(node, "amountIsToday", true));
        inputs.setHorizonMode(readMode(node.get("horizonMode")));
        inputs.setYearsToGoal(intField(node, "yearsToGoal"));
        inputs.setMonthsToGoal(intField(node, "monthsToGoal"));
        inputs.setCurrentAge(intField(node, "currentAge"));
        inputs.setGoalAge(intField(node, "goalAge"));
        inputs.setTargetYear(intField(node, "targetYear"));
        inputs.setTargetMonth(intField(node, "targetMonth"));
        return inputs;
    }

    private static TimeHorizonMode readMode(JsonNode node) {
        if (node == null || node.isNull()) {
            return TimeHorizonMode.YEARS;
        }
        try {
            return TimeHorizonMode.valueOf(node.asString().toUpperCase());
        } catch (final IllegalArgumentException ignored) {
            return TimeHorizonMode.YEARS;
        }
    }

    private static BigDecimal bd(JsonNode node, String field) {
        final JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.asString());
    }

    private static Integer intField(JsonNode node, String field) {
        final JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return Integer.valueOf(value.asString());
    }

    private static boolean boolField(JsonNode node, String field, boolean fallback) {
        final JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        return Boolean.parseBoolean(value.asString());
    }

    private static InflationInputs copy(InflationInputs source) {
        return new InflationInputs(
                source.getAmount(),
                source.getInflationRatePct(),
                source.getInflationVariationPct(),
                source.isAmountIsToday(),
                source.getHorizonMode(),
                source.getYearsToGoal(),
                source.getMonthsToGoal(),
                source.getCurrentAge(),
                source.getGoalAge(),
                source.getTargetYear(),
                source.getTargetMonth());
    }

    private static InflationInputs fallback() {
        final var inputs = new InflationInputs();
        inputs.setAmount(BigDecimal.valueOf(1_000_000));
        inputs.setInflationRatePct(BigDecimal.valueOf(6));
        inputs.setAmountIsToday(true);
        inputs.setHorizonMode(TimeHorizonMode.YEARS);
        inputs.setYearsToGoal(10);
        inputs.setMonthsToGoal(0);
        inputs.setCurrentAge(35);
        inputs.setGoalAge(45);
        inputs.setTargetYear(java.time.Year.now().getValue() + 10);
        inputs.setTargetMonth(12);
        return inputs;
    }
}
