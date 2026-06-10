package io.binarycodes.calculators.investment.service;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.WebStorage;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import io.binarycodes.calculators.base.common.TimeHorizonMode;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.investment.domain.ContributionFrequency;
import io.binarycodes.calculators.investment.domain.InvestmentInputs;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Per-session, per-currency cache of investment-calculator inputs, mirrored to
 * browser localStorage under {@value #STORAGE_KEY}.
 */
@Component
@VaadinSessionScope
public class InvestmentInputsStore {

    static final String STORAGE_KEY = "iv_inputs";
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final Map<SupportedCurrency, InvestmentInputs> cache = new EnumMap<>(SupportedCurrency.class);

    public void load(Consumer<Map<SupportedCurrency, InvestmentInputs>> onLoaded) {
        final UI ui = UI.getCurrent();
        if (ui == null) {
            onLoaded.accept(Map.copyOf(this.cache));
            return;
        }
        WebStorage.getItem(STORAGE_KEY, raw -> {
            if (raw != null && !raw.isBlank()) {
                try {
                    final JsonNode root = this.objectMapper.readTree(raw);
                    for (final SupportedCurrency currency : SupportedCurrency.values()) {
                        final JsonNode node = root.get(currency.name());
                        if (node != null) {
                            this.cache.put(currency, toInputs(node));
                        }
                    }
                } catch (final Exception ignored) { /* corrupt blob → fall back */ }
            }
            onLoaded.accept(Map.copyOf(this.cache));
        });
    }

    public InvestmentInputs get(SupportedCurrency currency) {
        return this.cache.get(currency);
    }

    public void save(SupportedCurrency currency, InvestmentInputs inputs) {
        this.cache.put(currency, inputs);
        persist();
    }

    private void persist() {
        final UI ui = UI.getCurrent();
        if (ui == null) {
            return;
        }
        final ObjectNode root = this.objectMapper.createObjectNode();
        for (final var entry : this.cache.entrySet()) {
            root.set(entry.getKey().name(), toJson(entry.getValue()));
        }
        WebStorage.setItem(STORAGE_KEY, root.toString());
    }

    private ObjectNode toJson(InvestmentInputs inputs) {
        final ObjectNode node = this.objectMapper.createObjectNode();
        node.put("amount", plain(inputs.getAmount()));
        node.put("frequency", inputs.getFrequency() == null
                ? ContributionFrequency.MONTHLY.name()
                : inputs.getFrequency().name());
        node.put("growthRate", plain(inputs.getGrowthRatePct()));
        node.put("taxRate", plain(inputs.getTaxRatePct()));
        node.put("inflationRate", plain(inputs.getInflationRatePct()));
        node.put("stepUp", plain(inputs.getStepUpPct()));
        node.put("horizonMode", inputs.getHorizonMode() == null
                ? TimeHorizonMode.YEARS.name()
                : inputs.getHorizonMode().name());
        node.put("investYears", intStr(inputs.getInvestYears()));
        node.put("investMonths", intStr(inputs.getInvestMonths()));
        node.put("currentAge", intStr(inputs.getCurrentAge()));
        node.put("goalAge", intStr(inputs.getGoalAge()));
        node.put("targetYear", intStr(inputs.getTargetYear()));
        node.put("targetMonth", intStr(inputs.getTargetMonth()));
        node.put("holdYears", intStr(inputs.getHoldYears()));
        node.put("holdMonths", intStr(inputs.getHoldMonths()));
        return node;
    }

    private static InvestmentInputs toInputs(JsonNode node) {
        final var inputs = new InvestmentInputs();
        inputs.setAmount(bd(node, "amount"));
        inputs.setFrequency(readFrequency(node.get("frequency")));
        inputs.setGrowthRatePct(bd(node, "growthRate"));
        inputs.setTaxRatePct(bd(node, "taxRate"));
        inputs.setInflationRatePct(bd(node, "inflationRate"));
        inputs.setStepUpPct(bd(node, "stepUp"));
        inputs.setHorizonMode(readMode(node.get("horizonMode")));
        inputs.setInvestYears(intField(node, "investYears"));
        inputs.setInvestMonths(intField(node, "investMonths"));
        inputs.setCurrentAge(intField(node, "currentAge"));
        inputs.setGoalAge(intField(node, "goalAge"));
        inputs.setTargetYear(intField(node, "targetYear"));
        inputs.setTargetMonth(intField(node, "targetMonth"));
        inputs.setHoldYears(intField(node, "holdYears"));
        inputs.setHoldMonths(intField(node, "holdMonths"));
        return inputs;
    }

    private static ContributionFrequency readFrequency(JsonNode node) {
        if (node == null || node.isNull()) {
            return ContributionFrequency.MONTHLY;
        }
        try {
            return ContributionFrequency.valueOf(node.asText().toUpperCase());
        } catch (final IllegalArgumentException ignored) {
            return ContributionFrequency.MONTHLY;
        }
    }

    private static TimeHorizonMode readMode(JsonNode node) {
        if (node == null || node.isNull()) {
            return TimeHorizonMode.YEARS;
        }
        try {
            return TimeHorizonMode.valueOf(node.asText().toUpperCase());
        } catch (final IllegalArgumentException ignored) {
            return TimeHorizonMode.YEARS;
        }
    }

    private static Integer intField(JsonNode node, String field) {
        final JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        return Integer.valueOf(value.asText());
    }

    private static BigDecimal bd(JsonNode node, String field) {
        final JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.asText());
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static String intStr(Integer value) {
        return value == null ? null : Integer.toString(value);
    }
}
