package io.binarycodes.calculators.irr.service;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.WebStorage;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import io.binarycodes.calculators.base.common.InputsStore;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.irr.domain.CashflowFrequency;
import io.binarycodes.calculators.irr.domain.DatedCashflow;
import io.binarycodes.calculators.irr.domain.RecurringCashflow;
import io.binarycodes.calculators.irr.domain.XirrInputs;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Per-session, per-currency cache of IRR/XIRR inputs, backed by browser
 * localStorage and reused for shareable links. Dates serialise as ISO-8601
 * strings; amounts as plain decimal strings.
 */
@Component
@VaadinSessionScope
public class XirrInputsStore implements InputsStore<XirrInputs> {

    static final String STORAGE_KEY = "xirr_inputs";
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final Map<SupportedCurrency, XirrInputs> cache = new EnumMap<>(SupportedCurrency.class);

    public void load(Consumer<Map<SupportedCurrency, XirrInputs>> onLoaded) {
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
                } catch (final Exception ignore) {
                    // Corrupt blob → fall back to defaults.
                }
            }
            onLoaded.accept(Map.copyOf(this.cache));
        });
    }

    public XirrInputs get(SupportedCurrency currency) {
        return this.cache.get(currency);
    }

    public void save(SupportedCurrency currency, XirrInputs inputs) {
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

    public ObjectNode toJsonNode(XirrInputs inputs) {
        final ObjectNode node = this.objectMapper.createObjectNode();
        node.set("oneOffInvestments", datedToJson(inputs.getOneOffInvestments()));
        node.set("recurringInvestments", recurringToJson(inputs.getRecurringInvestments()));
        node.set("oneOffWithdrawals", datedToJson(inputs.getOneOffWithdrawals()));
        node.set("recurringWithdrawals", recurringToJson(inputs.getRecurringWithdrawals()));
        return node;
    }

    private ArrayNode datedToJson(List<DatedCashflow> items) {
        final ArrayNode array = this.objectMapper.createArrayNode();
        if (items == null) {
            return array;
        }
        for (final DatedCashflow item : items) {
            final ObjectNode node = this.objectMapper.createObjectNode();
            node.put("date", item.getDate() == null ? null : item.getDate().toString());
            node.put("description", item.getDescription());
            node.put("amount", plain(item.getAmount()));
            array.add(node);
        }
        return array;
    }

    private ArrayNode recurringToJson(List<RecurringCashflow> items) {
        final ArrayNode array = this.objectMapper.createArrayNode();
        if (items == null) {
            return array;
        }
        for (final RecurringCashflow item : items) {
            final ObjectNode node = this.objectMapper.createObjectNode();
            node.put("startDate", item.getStartDate() == null ? null : item.getStartDate().toString());
            node.put("frequency", item.getFrequency() == null ? null : item.getFrequency().name());
            node.put("count", item.getCount() == null ? null : Integer.toString(item.getCount()));
            node.put("description", item.getDescription());
            node.put("amount", plain(item.getAmount()));
            array.add(node);
        }
        return array;
    }

    public XirrInputs fromJsonNode(JsonNode node) {
        final var inputs = new XirrInputs();
        inputs.setOneOffInvestments(readDated(node.get("oneOffInvestments")));
        inputs.setRecurringInvestments(readRecurring(node.get("recurringInvestments")));
        inputs.setOneOffWithdrawals(readDated(node.get("oneOffWithdrawals")));
        inputs.setRecurringWithdrawals(readRecurring(node.get("recurringWithdrawals")));
        return inputs;
    }

    private static List<DatedCashflow> readDated(JsonNode arrayNode) {
        final List<DatedCashflow> out = new ArrayList<>();
        if (arrayNode == null || !arrayNode.isArray()) {
            return out;
        }
        for (final JsonNode entry : arrayNode) {
            final var item = new DatedCashflow();
            item.setDate(date(entry.get("date")));
            item.setDescription(text(entry.get("description")));
            item.setAmount(amount(entry.get("amount")));
            out.add(item);
        }
        return out;
    }

    private static List<RecurringCashflow> readRecurring(JsonNode arrayNode) {
        final List<RecurringCashflow> out = new ArrayList<>();
        if (arrayNode == null || !arrayNode.isArray()) {
            return out;
        }
        for (final JsonNode entry : arrayNode) {
            final var item = new RecurringCashflow();
            item.setStartDate(date(entry.get("startDate")));
            item.setFrequency(frequency(entry.get("frequency")));
            item.setCount(integer(entry.get("count")));
            item.setDescription(text(entry.get("description")));
            item.setAmount(amount(entry.get("amount")));
            out.add(item);
        }
        return out;
    }

    private static CashflowFrequency frequency(JsonNode node) {
        if (node == null || node.isNull()) {
            return CashflowFrequency.MONTHLY;
        }
        try {
            return CashflowFrequency.valueOf(node.asString().toUpperCase());
        } catch (final IllegalArgumentException ignored) {
            return CashflowFrequency.MONTHLY;
        }
    }

    private static LocalDate date(JsonNode node) {
        if (node == null || node.isNull() || node.asString().isBlank()) {
            return null;
        }
        return LocalDate.parse(node.asString());
    }

    private static Integer integer(JsonNode node) {
        if (node == null || node.isNull() || node.asString().isBlank()) {
            return null;
        }
        return Integer.valueOf(node.asString());
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asString();
    }

    private static BigDecimal amount(JsonNode node) {
        if (node == null || node.isNull() || node.asString().isBlank()) {
            return null;
        }
        return new BigDecimal(node.asString());
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
