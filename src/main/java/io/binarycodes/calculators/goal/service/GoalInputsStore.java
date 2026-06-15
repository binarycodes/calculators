package io.binarycodes.calculators.goal.service;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.WebStorage;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import io.binarycodes.calculators.base.common.InputsStore;
import io.binarycodes.calculators.base.common.TimeHorizonMode;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.goal.domain.GoalInputs;
import io.binarycodes.calculators.goal.domain.Investment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Per-session, per-currency cache of goal-planner inputs, mirrored to browser
 * localStorage under {@value #STORAGE_KEY}. The serialised shape matches
 * {@code goal-defaults.json} so users with one already saved can roll over.
 */
@Component
@VaadinSessionScope
public class GoalInputsStore implements InputsStore<GoalInputs> {

    static final String STORAGE_KEY = "gp_inputs";
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final Map<SupportedCurrency, GoalInputs> cache = new EnumMap<>(SupportedCurrency.class);

    public void load(Consumer<Map<SupportedCurrency, GoalInputs>> onLoaded) {
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

    public GoalInputs get(SupportedCurrency currency) {
        return this.cache.get(currency);
    }

    public void save(SupportedCurrency currency, GoalInputs inputs) {
        this.cache.put(currency, inputs);
        persist();
    }

    public void clear(SupportedCurrency currency) {
        if (this.cache.remove(currency) != null) {
            persist();
        }
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

    /**
     * Serialise one currency's inputs to JSON (shared by persistence and share links).
     */
    public ObjectNode toJsonNode(GoalInputs inputs) {
        final ObjectNode node = this.objectMapper.createObjectNode();
        node.put("goalAmount", plain(inputs.getGoalAmount()));
        node.put("inflationRate", plain(inputs.getInflationRatePct()));
        node.put("horizonMode", inputs.getHorizonMode() == null
                ? TimeHorizonMode.YEARS.name()
                : inputs.getHorizonMode().name());
        node.put("yearsToGoal", intStr(inputs.getYearsToGoal()));
        node.put("monthsToGoal", intStr(inputs.getMonthsToGoal()));
        node.put("currentAge", intStr(inputs.getCurrentAge()));
        node.put("goalAge", intStr(inputs.getGoalAge()));
        node.put("targetYear", intStr(inputs.getTargetYear()));
        node.put("targetMonth", intStr(inputs.getTargetMonth()));

        final ArrayNode investmentsArray = this.objectMapper.createArrayNode();
        if (inputs.getInvestments() != null) {
            for (final Investment investment : inputs.getInvestments()) {
                final ObjectNode entry = this.objectMapper.createObjectNode();
                entry.put("label", investment.getLabel());
                entry.put("currentCorpus", plain(investment.getCurrentCorpus()));
                entry.put("growthRate", plain(investment.getGrowthRatePct()));
                entry.put("withdrawalTaxRate", plain(investment.getWithdrawalTaxRatePct()));
                entry.put("allocationPct", plain(investment.getAllocationPct()));
                entry.put("stepUp", plain(investment.getStepUpPct()));
                investmentsArray.add(entry);
            }
        }
        node.set("investments", investmentsArray);
        return node;
    }

    /**
     * Reconstruct inputs from JSON produced by {@link #toJsonNode}.
     */
    public GoalInputs fromJsonNode(JsonNode node) {
        final var inputs = new GoalInputs();
        inputs.setGoalAmount(bd(node, "goalAmount"));
        inputs.setInflationRatePct(bd(node, "inflationRate"));
        inputs.setHorizonMode(readMode(node.get("horizonMode")));
        inputs.setYearsToGoal(intField(node, "yearsToGoal"));
        inputs.setMonthsToGoal(intField(node, "monthsToGoal"));
        inputs.setCurrentAge(intField(node, "currentAge"));
        inputs.setGoalAge(intField(node, "goalAge"));
        inputs.setTargetYear(intField(node, "targetYear"));
        inputs.setTargetMonth(intField(node, "targetMonth"));
        inputs.setInvestments(readInvestments(node.get("investments")));
        return inputs;
    }

    private static List<Investment> readInvestments(JsonNode arrayNode) {
        final List<Investment> out = new ArrayList<>();
        if (arrayNode == null || !arrayNode.isArray()) {
            return out;
        }
        for (final JsonNode entry : arrayNode) {
            final var investment = new Investment();
            if (entry.has("label") && !entry.get("label").isNull()) {
                investment.setLabel(entry.get("label").asString());
            }
            investment.setCurrentCorpus(bd(entry, "currentCorpus"));
            investment.setGrowthRatePct(bd(entry, "growthRate"));
            investment.setWithdrawalTaxRatePct(bd(entry, "withdrawalTaxRate"));
            investment.setAllocationPct(bd(entry, "allocationPct"));
            investment.setStepUpPct(bd(entry, "stepUp"));
            out.add(investment);
        }
        return out;
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

    private static Integer intField(JsonNode node, String field) {
        final JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asString().isBlank()) {
            return null;
        }
        return Integer.valueOf(value.asString());
    }

    private static BigDecimal bd(JsonNode node, String field) {
        final JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.asString());
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static String intStr(Integer value) {
        return value == null ? null : Integer.toString(value);
    }
}
