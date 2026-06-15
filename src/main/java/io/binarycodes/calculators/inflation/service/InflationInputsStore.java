package io.binarycodes.calculators.inflation.service;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.WebStorage;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import io.binarycodes.calculators.base.common.InputsStore;
import io.binarycodes.calculators.base.common.TimeHorizonMode;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.inflation.domain.InflationInputs;
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
 * Per-session, per-currency cache of inflation-projection inputs, mirrored to
 * browser localStorage under {@value #STORAGE_KEY}.
 */
@Component
@VaadinSessionScope
public class InflationInputsStore implements InputsStore<InflationInputs> {

    static final String STORAGE_KEY = "ip_inputs";
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final Map<SupportedCurrency, InflationInputs> cache = new EnumMap<>(SupportedCurrency.class);

    public void load(Consumer<Map<SupportedCurrency, InflationInputs>> onLoaded) {
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
                            this.cache.put(currency, fromJsonNode(node));
                        }
                    }
                } catch (final Exception ignored) { /* corrupt blob → fall back */ }
            }
            onLoaded.accept(Map.copyOf(this.cache));
        });
    }

    public InflationInputs get(SupportedCurrency currency) {
        return this.cache.get(currency);
    }

    public void save(SupportedCurrency currency, InflationInputs inputs) {
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
            root.set(entry.getKey().name(), toJsonNode(entry.getValue()));
        }
        WebStorage.setItem(STORAGE_KEY, root.toString());
    }

    /** Serialise one currency's inputs to JSON (shared by persistence and share links). */
    public ObjectNode toJsonNode(InflationInputs inputs) {
        final ObjectNode node = this.objectMapper.createObjectNode();
        node.put("amount", plain(inputs.getAmount()));
        node.put("inflationRate", plain(inputs.getInflationRatePct()));
        node.put("amountIsToday", Boolean.toString(inputs.isAmountIsToday()));
        node.put("horizonMode", inputs.getHorizonMode() == null
                ? TimeHorizonMode.YEARS.name()
                : inputs.getHorizonMode().name());
        node.put("yearsToGoal", intStr(inputs.getYearsToGoal()));
        node.put("monthsToGoal", intStr(inputs.getMonthsToGoal()));
        node.put("currentAge", intStr(inputs.getCurrentAge()));
        node.put("goalAge", intStr(inputs.getGoalAge()));
        node.put("targetYear", intStr(inputs.getTargetYear()));
        node.put("targetMonth", intStr(inputs.getTargetMonth()));
        return node;
    }

    /** Reconstruct inputs from JSON produced by {@link #toJsonNode}. */
    public InflationInputs fromJsonNode(JsonNode node) {
        final var inputs = new InflationInputs();
        inputs.setAmount(bd(node, "amount"));
        inputs.setInflationRatePct(bd(node, "inflationRate"));
        inputs.setAmountIsToday(boolField(node, "amountIsToday"));
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

    private static boolean boolField(JsonNode node, String field) {
        final JsonNode value = node.get(field);
        return value != null && !value.isNull() && Boolean.parseBoolean(value.asText());
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
