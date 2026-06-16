package io.binarycodes.calculators.retirement.service;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.WebStorage;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import io.binarycodes.calculators.base.common.InputsStore;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.retirement.domain.Frequency;
import io.binarycodes.calculators.retirement.domain.FutureExpense;
import io.binarycodes.calculators.retirement.domain.FutureIncome;
import io.binarycodes.calculators.retirement.domain.RecurringExpense;
import io.binarycodes.calculators.retirement.domain.RecurringIncome;
import io.binarycodes.calculators.retirement.domain.RetirementBenefit;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;
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
 * Per-session, per-currency cache of retirement-calculator inputs. Mirrors the
 * legacy {@code rc_inputs} localStorage shape from the JS app:
 *
 * <pre>{ "INR": { "currentAge":"38", "retireAge":"45", ... }, "EUR": {...}, "USD": {...} }</pre>
 */
@Component
@VaadinSessionScope
public class RetirementInputsStore implements InputsStore<RetirementInputs> {

    static final String STORAGE_KEY = "rc_inputs";
    private final ObjectMapper om = JsonMapper.builder().build();
    private final Map<SupportedCurrency, RetirementInputs> cache = new EnumMap<>(SupportedCurrency.class);

    /**
     * Load all currencies from localStorage. {@code onLoaded} is called once
     * the asynchronous fetch completes (or immediately if there's no UI).
     */
    public void load(Consumer<Map<SupportedCurrency, RetirementInputs>> onLoaded) {
        final UI ui = UI.getCurrent();
        if (ui == null) {
            onLoaded.accept(Map.copyOf(this.cache));
            return;
        }
        WebStorage.getItem(STORAGE_KEY, raw -> {
            if (raw != null && !raw.isBlank()) {
                try {
                    final JsonNode root = this.om.readTree(raw);
                    for (final SupportedCurrency c : SupportedCurrency.values()) {
                        final JsonNode node = root.get(c.name());
                        if (node != null) {
                            this.cache.put(c, fromJsonNode(node));
                        }
                    }
                } catch (final Exception ignore) { /* corrupt blob → fall back */ }
            }
            onLoaded.accept(Map.copyOf(this.cache));
        });
    }

    public RetirementInputs get(SupportedCurrency c) {
        return this.cache.get(c);
    }

    /**
     * Save inputs for the given currency. Updates in-memory + localStorage.
     */
    public void save(SupportedCurrency c, RetirementInputs in) {
        this.cache.put(c, in);
        persist();
    }

    /**
     * Drop the entry for the given currency (e.g. on reset to defaults).
     */
    public void clear(SupportedCurrency c) {
        if (this.cache.remove(c) != null) {
            persist();
        }
    }

    private void persist() {
        final UI ui = UI.getCurrent();
        if (ui == null) {
            return;
        }
        final ObjectNode root = this.om.createObjectNode();
        for (final var e : this.cache.entrySet()) {
            root.set(e.getKey().name(), toJsonNode(e.getValue()));
        }
        WebStorage.setItem(STORAGE_KEY, root.toString());
    }

    /** Serialise one currency's inputs to JSON (shared by persistence and share links). */
    public ObjectNode toJsonNode(RetirementInputs in) {
        final ObjectNode n = this.om.createObjectNode();
        n.put("currentAge", in.getCurrentAge() == null ? null : Integer.toString(in.getCurrentAge()));
        n.put("retireAge",  in.getRetireAge()  == null ? null : Integer.toString(in.getRetireAge()));
        n.put("lifeExp",    in.getLifeExp()    == null ? null : Integer.toString(in.getLifeExp()));
        n.put("corpus",         plain(in.getCorpus()));
        n.put("monthlyExp",     plain(in.getMonthlyExpenses()));
        n.put("inflation",      plain(in.getInflationPct()));
        n.put("growthPre",      plain(in.getGrowthPrePct()));
        n.put("growthPost",     plain(in.getGrowthPostPct()));
        n.put("corpusTaxRate",  plain(in.getCorpusTaxRatePct()));
        n.put("monthlyInvPre",  plain(in.getMonthlyInvPre()));
        n.put("sipGrowthPre",   plain(in.getSipGrowthPrePct()));
        n.put("sipStepUpPre",   plain(in.getSipStepUpPrePct()));
        n.put("taxRatePre",     plain(in.getTaxRatePrePct()));
        n.put("monthlyInvPost", plain(in.getMonthlyInvPost()));
        n.put("sipGrowthPost",  plain(in.getSipGrowthPostPct()));
        n.put("sipStepUpPost",  plain(in.getSipStepUpPostPct()));
        n.put("taxRatePost",    plain(in.getTaxRatePostPct()));
        n.set("futureExpenses", futureExpensesToJson(in.getFutureExpenses()));
        n.set("retirementBenefits", retirementBenefitsToJson(in.getRetirementBenefits()));
        n.set("futureIncomes", futureIncomesToJson(in.getFutureIncomes()));
        n.set("recurringExpenses", recurringExpensesToJson(in.getRecurringExpenses()));
        n.set("recurringIncomes", recurringIncomesToJson(in.getRecurringIncomes()));
        return n;
    }

    private ArrayNode recurringExpensesToJson(List<RecurringExpense> expenses) {
        final ArrayNode arr = this.om.createArrayNode();
        if (expenses == null) {
            return arr;
        }
        for (final RecurringExpense expense : expenses) {
            final ObjectNode node = this.om.createObjectNode();
            node.put("year", expense.getYear() == null ? null : Integer.toString(expense.getYear()));
            node.put("stopYear", expense.getStopYear() == null ? null : Integer.toString(expense.getStopYear()));
            node.put("description", expense.getDescription());
            node.put("frequency", expense.getFrequency() == null ? null : expense.getFrequency().name());
            node.put("amount", plain(expense.getAmount()));
            node.put("inflation", plain(expense.getInflationPct()));
            arr.add(node);
        }
        return arr;
    }

    private ArrayNode recurringIncomesToJson(List<RecurringIncome> incomes) {
        final ArrayNode arr = this.om.createArrayNode();
        if (incomes == null) {
            return arr;
        }
        for (final RecurringIncome income : incomes) {
            final ObjectNode node = this.om.createObjectNode();
            node.put("year", income.getYear() == null ? null : Integer.toString(income.getYear()));
            node.put("stopYear", income.getStopYear() == null ? null : Integer.toString(income.getStopYear()));
            node.put("description", income.getDescription());
            node.put("frequency", income.getFrequency() == null ? null : income.getFrequency().name());
            node.put("amount", plain(income.getAmount()));
            node.put("taxRate", plain(income.getTaxRatePct()));
            arr.add(node);
        }
        return arr;
    }

    private ArrayNode futureIncomesToJson(List<FutureIncome> incomes) {
        final ArrayNode arr = this.om.createArrayNode();
        if (incomes == null) {
            return arr;
        }
        for (final FutureIncome income : incomes) {
            final ObjectNode node = this.om.createObjectNode();
            node.put("year", income.getYear() == null ? null : Integer.toString(income.getYear()));
            node.put("description", income.getDescription());
            node.put("amount", plain(income.getAmount()));
            node.put("taxRate", plain(income.getTaxRatePct()));
            arr.add(node);
        }
        return arr;
    }

    private ArrayNode retirementBenefitsToJson(List<RetirementBenefit> benefits) {
        final ArrayNode arr = this.om.createArrayNode();
        if (benefits == null) {
            return arr;
        }
        for (final RetirementBenefit benefit : benefits) {
            final ObjectNode node = this.om.createObjectNode();
            node.put("description", benefit.getDescription());
            node.put("amount", plain(benefit.getAmount()));
            node.put("taxRate", plain(benefit.getTaxRatePct()));
            arr.add(node);
        }
        return arr;
    }

    private ArrayNode futureExpensesToJson(List<FutureExpense> expenses) {
        final ArrayNode arr = this.om.createArrayNode();
        if (expenses == null) {
            return arr;
        }
        for (final FutureExpense expense : expenses) {
            final ObjectNode node = this.om.createObjectNode();
            node.put("year", expense.getYear() == null ? null : Integer.toString(expense.getYear()));
            node.put("description", expense.getDescription());
            node.put("amount", plain(expense.getAmount()));
            node.put("inflation", plain(expense.getInflationPct()));
            arr.add(node);
        }
        return arr;
    }

    private static String plain(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }

    /** Reconstruct inputs from JSON produced by {@link #toJsonNode}. */
    public RetirementInputs fromJsonNode(JsonNode n) {
        final var inputs = new RetirementInputs();
        inputs.setCurrentAge(n.get("currentAge").asInt());
        inputs.setRetireAge(n.get("retireAge").asInt());
        inputs.setLifeExp(n.get("lifeExp").asInt());
        inputs.setCorpus(bd(n, "corpus"));
        inputs.setMonthlyExpenses(bd(n, "monthlyExp"));
        inputs.setInflationPct(bd(n, "inflation"));
        inputs.setGrowthPrePct(bd(n, "growthPre"));
        inputs.setGrowthPostPct(bd(n, "growthPost"));
        inputs.setCorpusTaxRatePct(bd(n, "corpusTaxRate"));
        inputs.setMonthlyInvPre(bd(n, "monthlyInvPre"));
        inputs.setSipGrowthPrePct(bd(n, "sipGrowthPre"));
        inputs.setSipStepUpPrePct(bd(n, "sipStepUpPre"));
        inputs.setTaxRatePrePct(bd(n, "taxRatePre"));
        inputs.setMonthlyInvPost(bd(n, "monthlyInvPost"));
        inputs.setSipGrowthPostPct(bd(n, "sipGrowthPost"));
        inputs.setSipStepUpPostPct(bd(n, "sipStepUpPost"));
        inputs.setTaxRatePostPct(bd(n, "taxRatePost"));
        inputs.setFutureExpenses(readFutureExpenses(n.get("futureExpenses")));
        inputs.setRetirementBenefits(readRetirementBenefits(n.get("retirementBenefits")));
        inputs.setFutureIncomes(readFutureIncomes(n.get("futureIncomes")));
        inputs.setRecurringExpenses(readRecurringExpenses(n.get("recurringExpenses")));
        inputs.setRecurringIncomes(readRecurringIncomes(n.get("recurringIncomes")));
        return inputs;
    }

    private static List<RecurringExpense> readRecurringExpenses(JsonNode arrayNode) {
        final List<RecurringExpense> out = new ArrayList<>();
        if (arrayNode == null || !arrayNode.isArray()) {
            return out;
        }
        for (final JsonNode entry : arrayNode) {
            final var expense = new RecurringExpense();
            if (entry.has("year") && !entry.get("year").isNull()) {
                expense.setYear(Integer.valueOf(entry.get("year").asString()));
            }
            if (entry.has("stopYear") && !entry.get("stopYear").isNull()) {
                expense.setStopYear(Integer.valueOf(entry.get("stopYear").asString()));
            }
            if (entry.has("description") && !entry.get("description").isNull()) {
                expense.setDescription(entry.get("description").asString());
            }
            expense.setFrequency(readFrequency(entry.get("frequency")));
            expense.setAmount(bd(entry, "amount"));
            expense.setInflationPct(bdOrNull(entry, "inflation"));
            out.add(expense);
        }
        return out;
    }

    private static List<RecurringIncome> readRecurringIncomes(JsonNode arrayNode) {
        final List<RecurringIncome> out = new ArrayList<>();
        if (arrayNode == null || !arrayNode.isArray()) {
            return out;
        }
        for (final JsonNode entry : arrayNode) {
            final var income = new RecurringIncome();
            if (entry.has("year") && !entry.get("year").isNull()) {
                income.setYear(Integer.valueOf(entry.get("year").asString()));
            }
            if (entry.has("stopYear") && !entry.get("stopYear").isNull()) {
                income.setStopYear(Integer.valueOf(entry.get("stopYear").asString()));
            }
            if (entry.has("description") && !entry.get("description").isNull()) {
                income.setDescription(entry.get("description").asString());
            }
            income.setFrequency(readFrequency(entry.get("frequency")));
            income.setAmount(bd(entry, "amount"));
            income.setTaxRatePct(bd(entry, "taxRate"));
            out.add(income);
        }
        return out;
    }

    private static Frequency readFrequency(JsonNode node) {
        if (node == null || node.isNull()) {
            return Frequency.MONTHLY;
        }
        try {
            return Frequency.valueOf(node.asString().toUpperCase());
        } catch (final IllegalArgumentException ignored) {
            return Frequency.MONTHLY;
        }
    }

    private static List<FutureIncome> readFutureIncomes(JsonNode arrayNode) {
        final List<FutureIncome> out = new ArrayList<>();
        if (arrayNode == null || !arrayNode.isArray()) {
            return out;
        }
        for (final JsonNode entry : arrayNode) {
            final var income = new FutureIncome();
            if (entry.has("year") && !entry.get("year").isNull()) {
                income.setYear(Integer.valueOf(entry.get("year").asString()));
            }
            if (entry.has("description") && !entry.get("description").isNull()) {
                income.setDescription(entry.get("description").asString());
            }
            income.setAmount(bd(entry, "amount"));
            income.setTaxRatePct(bd(entry, "taxRate"));
            out.add(income);
        }
        return out;
    }

    private static List<RetirementBenefit> readRetirementBenefits(JsonNode arrayNode) {
        final List<RetirementBenefit> out = new ArrayList<>();
        if (arrayNode == null || !arrayNode.isArray()) {
            return out;
        }
        for (final JsonNode entry : arrayNode) {
            final var benefit = new RetirementBenefit();
            if (entry.has("description") && !entry.get("description").isNull()) {
                benefit.setDescription(entry.get("description").asString());
            }
            benefit.setAmount(bd(entry, "amount"));
            benefit.setTaxRatePct(bd(entry, "taxRate"));
            out.add(benefit);
        }
        return out;
    }

    private static List<FutureExpense> readFutureExpenses(JsonNode arrayNode) {
        final List<FutureExpense> out = new ArrayList<>();
        if (arrayNode == null || !arrayNode.isArray()) {
            return out;
        }
        for (final JsonNode entry : arrayNode) {
            final var expense = new FutureExpense();
            if (entry.has("year") && !entry.get("year").isNull()) {
                expense.setYear(Integer.valueOf(entry.get("year").asString()));
            }
            if (entry.has("description") && !entry.get("description").isNull()) {
                expense.setDescription(entry.get("description").asString());
            }
            expense.setAmount(bd(entry, "amount"));
            expense.setInflationPct(bd(entry, "inflation"));
            out.add(expense);
        }
        return out;
    }

    private static BigDecimal bd(JsonNode n, String field) {
        final JsonNode v = n.get(field);
        if (v == null || v.isNull()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(v.asString());
    }

    private static BigDecimal bdOrNull(JsonNode n, String field) {
        final JsonNode v = n.get(field);
        if (v == null || v.isNull() || v.asString().isBlank()) {
            return null;
        }
        return new BigDecimal(v.asString());
    }
}
