package io.binarycodes.calculators.irr.service;

import io.binarycodes.calculators.base.common.CalculatorDefaults;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.irr.domain.CashflowFrequency;
import io.binarycodes.calculators.irr.domain.DatedCashflow;
import io.binarycodes.calculators.irr.domain.RecurringCashflow;
import io.binarycodes.calculators.irr.domain.XirrInputs;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Loads {@code xirr-defaults.json} into a sample {@link XirrInputs} per currency
 * — a three-year monthly SIP redeemed at a current value, which exercises every
 * part of the calculator. Each {@link #forCurrency} call returns a deep copy so
 * editing the form never mutates the cached default.
 */
@Service
public class XirrDefaultsProvider implements CalculatorDefaults<XirrInputs> {

    private final Resource defaultsResource;
    private final Map<SupportedCurrency, XirrInputs> defaults = new EnumMap<>(SupportedCurrency.class);

    public XirrDefaultsProvider(@Value("classpath:xirr-defaults.json") Resource defaultsResource) {
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

    public XirrInputs forCurrency(SupportedCurrency currency) {
        final XirrInputs inputs = this.defaults.get(currency);
        if (inputs != null) {
            return copy(inputs);
        }
        final XirrInputs inrDefault = this.defaults.get(SupportedCurrency.INR);
        return inrDefault == null ? new XirrInputs() : copy(inrDefault);
    }

    private static XirrInputs toInputs(JsonNode node) {
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
            out.add(new DatedCashflow(date(entry.get("date")), text(entry.get("description")),
                    amount(entry.get("amount"))));
        }
        return out;
    }

    private static List<RecurringCashflow> readRecurring(JsonNode arrayNode) {
        final List<RecurringCashflow> out = new ArrayList<>();
        if (arrayNode == null || !arrayNode.isArray()) {
            return out;
        }
        for (final JsonNode entry : arrayNode) {
            out.add(new RecurringCashflow(date(entry.get("startDate")), frequency(entry.get("frequency")),
                    integer(entry.get("count")), text(entry.get("description")), amount(entry.get("amount"))));
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
        return node == null || node.isNull() || node.asString().isBlank() ? null : LocalDate.parse(node.asString());
    }

    private static Integer integer(JsonNode node) {
        return node == null || node.isNull() || node.asString().isBlank() ? null : Integer.valueOf(node.asString());
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asString();
    }

    private static BigDecimal amount(JsonNode node) {
        return node == null || node.isNull() || node.asString().isBlank() ? null : new BigDecimal(node.asString());
    }

    private static XirrInputs copy(XirrInputs source) {
        final var copy = new XirrInputs();
        copy.setOneOffInvestments(copyDated(source.getOneOffInvestments()));
        copy.setRecurringInvestments(copyRecurring(source.getRecurringInvestments()));
        copy.setOneOffWithdrawals(copyDated(source.getOneOffWithdrawals()));
        copy.setRecurringWithdrawals(copyRecurring(source.getRecurringWithdrawals()));
        return copy;
    }

    private static List<DatedCashflow> copyDated(List<DatedCashflow> source) {
        final List<DatedCashflow> out = new ArrayList<>();
        for (final DatedCashflow item : source) {
            out.add(new DatedCashflow(item.getDate(), item.getDescription(), item.getAmount()));
        }
        return out;
    }

    private static List<RecurringCashflow> copyRecurring(List<RecurringCashflow> source) {
        final List<RecurringCashflow> out = new ArrayList<>();
        for (final RecurringCashflow item : source) {
            out.add(new RecurringCashflow(item.getStartDate(), item.getFrequency(), item.getCount(),
                    item.getDescription(), item.getAmount()));
        }
        return out;
    }
}
