package io.binarycodes.calculators.loan.service;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.WebStorage;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import io.binarycodes.calculators.base.common.InputsStore;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.loan.domain.LoanInputs;
import io.binarycodes.calculators.base.common.Frequency;
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
 * Per-session, per-currency cache of loan / EMI inputs, mirrored to browser
 * localStorage under {@value #STORAGE_KEY}.
 */
@Component
@VaadinSessionScope
public class LoanInputsStore implements InputsStore<LoanInputs> {

    static final String STORAGE_KEY = "ln_inputs";
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final Map<SupportedCurrency, LoanInputs> cache = new EnumMap<>(SupportedCurrency.class);

    public void load(Consumer<Map<SupportedCurrency, LoanInputs>> onLoaded) {
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

    public LoanInputs get(SupportedCurrency currency) {
        return this.cache.get(currency);
    }

    public void save(SupportedCurrency currency, LoanInputs inputs) {
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
    public ObjectNode toJsonNode(LoanInputs inputs) {
        final ObjectNode node = this.objectMapper.createObjectNode();
        node.put("loanAmount", plain(inputs.getLoanAmount()));
        node.put("annualRate", plain(inputs.getAnnualRatePct()));
        node.put("tenureYears", intStr(inputs.getTenureYears()));
        node.put("tenureMonths", intStr(inputs.getTenureMonths()));
        node.put("inflationRate", plain(inputs.getInflationRatePct()));
        node.put("extraPerPeriod", plain(inputs.getExtraPerPeriod()));
        node.put("extraFrequency", inputs.getExtraFrequency() == null
                ? Frequency.YEARLY.name()
                : inputs.getExtraFrequency().name());
        node.put("extraEmisPerYear", intStr(inputs.getExtraEmisPerYear()));
        node.put("emiStepUp", plain(inputs.getEmiStepUpPct()));
        return node;
    }

    /** Reconstruct inputs from JSON produced by {@link #toJsonNode}. */
    public LoanInputs fromJsonNode(JsonNode node) {
        final var inputs = new LoanInputs();
        inputs.setLoanAmount(bd(node, "loanAmount"));
        inputs.setAnnualRatePct(bd(node, "annualRate"));
        inputs.setTenureYears(intField(node, "tenureYears"));
        inputs.setTenureMonths(intField(node, "tenureMonths"));
        inputs.setInflationRatePct(bd(node, "inflationRate"));
        inputs.setExtraPerPeriod(bd(node, "extraPerPeriod"));
        inputs.setExtraFrequency(readFrequency(node.get("extraFrequency")));
        inputs.setExtraEmisPerYear(intField(node, "extraEmisPerYear"));
        inputs.setEmiStepUpPct(bd(node, "emiStepUp"));
        return inputs;
    }

    private static Frequency readFrequency(JsonNode node) {
        if (node == null || node.isNull()) {
            return Frequency.YEARLY;
        }
        try {
            return Frequency.valueOf(node.asString().toUpperCase());
        } catch (final IllegalArgumentException ignored) {
            return Frequency.YEARLY;
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
