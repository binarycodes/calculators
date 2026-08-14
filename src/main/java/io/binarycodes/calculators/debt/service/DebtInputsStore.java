package io.binarycodes.calculators.debt.service;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.WebStorage;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import io.binarycodes.calculators.base.common.InputsStore;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.debt.domain.Debt;
import io.binarycodes.calculators.debt.domain.DebtPlanInputs;
import io.binarycodes.calculators.debt.domain.PayoffStrategy;
import io.binarycodes.calculators.debt.domain.Windfall;
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
 * Per-session, per-currency cache of debt-planner inputs, mirrored to browser
 * localStorage under {@value #STORAGE_KEY}. The debts list round-trips as a JSON
 * array of per-debt objects.
 */
@Component
@VaadinSessionScope
public class DebtInputsStore implements InputsStore<DebtPlanInputs> {

    static final String STORAGE_KEY = "dbt_inputs";
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final Map<SupportedCurrency, DebtPlanInputs> cache = new EnumMap<>(SupportedCurrency.class);

    @Override
    public void load(Consumer<Map<SupportedCurrency, DebtPlanInputs>> onLoaded) {
        final UI ui = UI.getCurrent();
        if (ui == null) {
            onLoaded.accept(Map.copyOf(this.cache));
            return;
        }
        WebStorage.getItem(STORAGE_KEY, raw -> {
            // The browser is the source of truth. Anything cached from an earlier
            // request must not outlive it, or a replayed session cookie would hand
            // back inputs the client no longer holds.
            this.cache.clear();
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

    @Override
    public DebtPlanInputs get(SupportedCurrency currency) {
        return this.cache.get(currency);
    }

    @Override
    public void save(SupportedCurrency currency, DebtPlanInputs inputs) {
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

    @Override
    public ObjectNode toJsonNode(DebtPlanInputs inputs) {
        final ObjectNode node = this.objectMapper.createObjectNode();
        node.put("monthlyBudget", plain(inputs.getMonthlyBudget()));
        node.put("budgetStepUpPct", plain(inputs.getBudgetStepUpPct()));
        node.put("defaultFeePerMonth", plain(inputs.getDefaultFeePerMonth()));
        node.put("strategy", inputs.getStrategy() == null ? null : inputs.getStrategy().name());
        node.put("inflationRatePct", plain(inputs.getInflationRatePct()));
        node.set("debts", debtsToJson(inputs.getDebts()));
        node.set("windfalls", windfallsToJson(inputs.getWindfalls()));
        return node;
    }

    @Override
    public DebtPlanInputs fromJsonNode(JsonNode node) {
        final var inputs = new DebtPlanInputs();
        inputs.setMonthlyBudget(bd(node, "monthlyBudget"));
        inputs.setBudgetStepUpPct(bd(node, "budgetStepUpPct"));
        inputs.setDefaultFeePerMonth(bd(node, "defaultFeePerMonth"));
        inputs.setStrategy(readStrategy(node.get("strategy")));
        inputs.setInflationRatePct(bd(node, "inflationRatePct"));
        inputs.setDebts(readDebts(node.get("debts")));
        inputs.setWindfalls(readWindfalls(node.get("windfalls")));
        return inputs;
    }

    private ArrayNode debtsToJson(List<Debt> debts) {
        final ArrayNode array = this.objectMapper.createArrayNode();
        if (debts == null) {
            return array;
        }
        for (final Debt debt : debts) {
            final ObjectNode node = this.objectMapper.createObjectNode();
            node.put("name", debt.getName());
            node.put("balance", plain(debt.getBalance()));
            node.put("aprPct", plain(debt.getAprPct()));
            node.put("minimumPayment", plain(debt.getMinimumPayment()));
            node.put("minimumPct", plain(debt.getMinimumPct()));
            node.put("promoAprPct", plain(debt.getPromoAprPct()));
            node.put("promoMonths", intStr(debt.getPromoMonths()));
            node.put("priority", debt.isPriority());
            array.add(node);
        }
        return array;
    }

    private ArrayNode windfallsToJson(List<Windfall> windfalls) {
        final ArrayNode array = this.objectMapper.createArrayNode();
        if (windfalls == null) {
            return array;
        }
        for (final Windfall windfall : windfalls) {
            final ObjectNode node = this.objectMapper.createObjectNode();
            node.put("month", intStr(windfall.getMonth()));
            node.put("amount", plain(windfall.getAmount()));
            array.add(node);
        }
        return array;
    }

    private static List<Windfall> readWindfalls(JsonNode arrayNode) {
        final List<Windfall> windfalls = new ArrayList<>();
        if (arrayNode == null || !arrayNode.isArray()) {
            return windfalls;
        }
        for (final JsonNode entry : arrayNode) {
            windfalls.add(new Windfall(intField(entry, "month"), bd(entry, "amount")));
        }
        return windfalls;
    }

    private static List<Debt> readDebts(JsonNode arrayNode) {
        final List<Debt> debts = new ArrayList<>();
        if (arrayNode == null || !arrayNode.isArray()) {
            return debts;
        }
        for (final JsonNode entry : arrayNode) {
            final var debt = new Debt();
            debt.setName(text(entry.get("name")));
            debt.setBalance(bd(entry, "balance"));
            debt.setAprPct(bd(entry, "aprPct"));
            debt.setMinimumPayment(bd(entry, "minimumPayment"));
            debt.setMinimumPct(bd(entry, "minimumPct"));
            debt.setPromoAprPct(bd(entry, "promoAprPct"));
            debt.setPromoMonths(intField(entry, "promoMonths"));
            final JsonNode priority = entry.get("priority");
            debt.setPriority(priority != null && priority.asBoolean());
            debts.add(debt);
        }
        return debts;
    }

    private static PayoffStrategy readStrategy(JsonNode value) {
        if (value == null || value.isNull() || value.asString().isBlank()) {
            return null;
        }
        try {
            return PayoffStrategy.valueOf(value.asString());
        } catch (final IllegalArgumentException unknownStrategy) {
            // A retired value (e.g. the old CUSTOM) falls back to the default.
            return null;
        }
    }

    private static String text(JsonNode value) {
        return value == null || value.isNull() ? null : value.asString();
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
            return null;
        }
        final String text = value.asString();
        if (text == null || text.isBlank()) {
            return null;
        }
        return new BigDecimal(text);
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static String intStr(Integer value) {
        return value == null ? null : Integer.toString(value);
    }
}
