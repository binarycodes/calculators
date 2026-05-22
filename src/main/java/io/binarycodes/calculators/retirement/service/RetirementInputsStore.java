package io.binarycodes.calculators.retirement.service;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.WebStorage;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.retirement.domain.FutureExpense;
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
public class RetirementInputsStore {

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
                            this.cache.put(c, toInputs(node));
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
            root.set(e.getKey().name(), toJson(e.getValue()));
        }
        WebStorage.setItem(STORAGE_KEY, root.toString());
    }

    private ObjectNode toJson(RetirementInputs in) {
        final ObjectNode n = this.om.createObjectNode();
        n.put("currentAge", in.getCurrentAge() == null ? null : Integer.toString(in.getCurrentAge()));
        n.put("retireAge",  in.getRetireAge()  == null ? null : Integer.toString(in.getRetireAge()));
        n.put("lifeExp",    in.getLifeExp()    == null ? null : Integer.toString(in.getLifeExp()));
        n.put("corpus",         plain(in.getCorpus()));
        n.put("monthlyExp",     plain(in.getMonthlyExpenses()));
        n.put("inflation",      plain(in.getInflationPct()));
        n.put("growthPre",      plain(in.getGrowthPrePct()));
        n.put("growthPost",     plain(in.getGrowthPostPct()));
        n.put("monthlyInvPre",  plain(in.getMonthlyInvPre()));
        n.put("sipGrowthPre",   plain(in.getSipGrowthPrePct()));
        n.put("sipStepUpPre",   plain(in.getSipStepUpPrePct()));
        n.put("monthlyInvPost", plain(in.getMonthlyInvPost()));
        n.put("sipGrowthPost",  plain(in.getSipGrowthPostPct()));
        n.put("sipStepUpPost",  plain(in.getSipStepUpPostPct()));
        n.put("taxRate",        plain(in.getTaxRatePct()));
        n.set("futureExpenses", futureExpensesToJson(in.getFutureExpenses()));
        return n;
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

    private RetirementInputs toInputs(JsonNode n) {
        final var inputs = new RetirementInputs();
        inputs.setCurrentAge(n.get("currentAge").asInt());
        inputs.setRetireAge(n.get("retireAge").asInt());
        inputs.setLifeExp(n.get("lifeExp").asInt());
        inputs.setCorpus(bd(n, "corpus"));
        inputs.setMonthlyExpenses(bd(n, "monthlyExp"));
        inputs.setInflationPct(bd(n, "inflation"));
        inputs.setGrowthPrePct(bd(n, "growthPre"));
        inputs.setGrowthPostPct(bd(n, "growthPost"));
        inputs.setMonthlyInvPre(bd(n, "monthlyInvPre"));
        inputs.setSipGrowthPrePct(bd(n, "sipGrowthPre"));
        inputs.setSipStepUpPrePct(bd(n, "sipStepUpPre"));
        inputs.setMonthlyInvPost(bd(n, "monthlyInvPost"));
        inputs.setSipGrowthPostPct(bd(n, "sipGrowthPost"));
        inputs.setSipStepUpPostPct(bd(n, "sipStepUpPost"));
        inputs.setTaxRatePct(bd(n, "taxRate"));
        inputs.setFutureExpenses(readFutureExpenses(n.get("futureExpenses")));
        return inputs;
    }

    private static List<FutureExpense> readFutureExpenses(JsonNode arrayNode) {
        final List<FutureExpense> out = new ArrayList<>();
        if (arrayNode == null || !arrayNode.isArray()) {
            return out;
        }
        for (final JsonNode entry : arrayNode) {
            final var expense = new FutureExpense();
            if (entry.has("year") && !entry.get("year").isNull()) {
                expense.setYear(Integer.valueOf(entry.get("year").asText()));
            }
            if (entry.has("description") && !entry.get("description").isNull()) {
                expense.setDescription(entry.get("description").asText());
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
        return new BigDecimal(v.asText());
    }
}
