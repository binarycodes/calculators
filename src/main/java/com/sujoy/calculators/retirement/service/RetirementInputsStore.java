package com.sujoy.calculators.retirement.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import com.sujoy.calculators.base.money.Currency;
import com.sujoy.calculators.retirement.domain.RetirementInputs;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.WebStorage;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumMap;
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
    private final Map<Currency, RetirementInputs> cache = new EnumMap<>(Currency.class);

    /** Load all currencies from localStorage. {@code onLoaded} is called once
     *  the asynchronous fetch completes (or immediately if there's no UI). */
    public void load(Consumer<Map<Currency, RetirementInputs>> onLoaded) {
        final UI ui = UI.getCurrent();
        if (ui == null) {
            onLoaded.accept(Map.copyOf(this.cache));
            return;
        }
        WebStorage.getItem(STORAGE_KEY, raw -> {
            if (raw != null && !raw.isBlank()) {
                try {
                    final JsonNode root = this.om.readTree(raw);
                    for (final Currency c : Currency.values()) {
                        final JsonNode node = root.get(c.name());
                        if (node != null) this.cache.put(c, toInputs(node));
                    }
                } catch (final Exception ignore) { /* corrupt blob → fall back */ }
            }
            onLoaded.accept(Map.copyOf(this.cache));
        });
    }

    public RetirementInputs get(Currency c) {
        return this.cache.get(c);
    }

    /** Save inputs for the given currency. Updates in-memory + localStorage. */
    public void save(Currency c, RetirementInputs in) {
        this.cache.put(c, in);
        persist();
    }

    /** Drop the entry for the given currency (e.g. on reset to defaults). */
    public void clear(Currency c) {
        if (this.cache.remove(c) != null) persist();
    }

    private void persist() {
        final UI ui = UI.getCurrent();
        if (ui == null) return;
        final ObjectNode root = this.om.createObjectNode();
        for (final var e : this.cache.entrySet()) root.set(e.getKey().name(), toJson(e.getValue()));
        WebStorage.setItem(STORAGE_KEY, root.toString());
    }

    private ObjectNode toJson(RetirementInputs in) {
        final ObjectNode n = this.om.createObjectNode();
        n.put("currentAge",    Integer.toString(in.currentAge()));
        n.put("retireAge",     Integer.toString(in.retireAge()));
        n.put("lifeExp",       Integer.toString(in.lifeExp()));
        n.put("corpus",        in.corpus().toPlainString());
        n.put("monthlyExp",    in.monthlyExpenses().toPlainString());
        n.put("inflation",     in.inflationPct().toPlainString());
        n.put("growthPre",     in.growthPrePct().toPlainString());
        n.put("growthPost",    in.growthPostPct().toPlainString());
        n.put("monthlyInvPre", in.monthlyInvPre().toPlainString());
        n.put("sipGrowthPre",  in.sipGrowthPrePct().toPlainString());
        n.put("monthlyInvPost", in.monthlyInvPost().toPlainString());
        n.put("sipGrowthPost", in.sipGrowthPostPct().toPlainString());
        return n;
    }

    private RetirementInputs toInputs(JsonNode n) {
        return new RetirementInputs(
                n.get("currentAge").asInt(),
                n.get("retireAge").asInt(),
                n.get("lifeExp").asInt(),
                bd(n, "corpus"),
                bd(n, "monthlyExp"),
                bd(n, "inflation"),
                bd(n, "growthPre"),
                bd(n, "growthPost"),
                bd(n, "monthlyInvPre"),
                bd(n, "sipGrowthPre"),
                bd(n, "monthlyInvPost"),
                bd(n, "sipGrowthPost"));
    }

    private static BigDecimal bd(JsonNode n, String field) {
        final JsonNode v = n.get(field);
        if (v == null || v.isNull()) return BigDecimal.ZERO;
        return new BigDecimal(v.asText());
    }
}
