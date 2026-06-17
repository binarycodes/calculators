package io.binarycodes.calculators.loan.service;

import io.binarycodes.calculators.base.common.CalculatorDefaults;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.loan.domain.LoanInputs;
import io.binarycodes.calculators.loan.domain.PrepaymentFrequency;
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
import java.util.EnumMap;
import java.util.Map;

/**
 * Loads {@code loan-defaults.json} from classpath into typed {@link LoanInputs}
 * per currency. Prepayment levers default to zero so the calculator opens as a
 * plain EMI calculator.
 */
@Service
public class LoanDefaultsProvider implements CalculatorDefaults<LoanInputs> {

    private final Resource defaultsResource;
    private final Map<SupportedCurrency, LoanInputs> defaults = new EnumMap<>(SupportedCurrency.class);

    public LoanDefaultsProvider(@Value("classpath:loan-defaults.json") Resource defaultsResource) {
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

    public LoanInputs forCurrency(SupportedCurrency currency) {
        final LoanInputs inputs = this.defaults.get(currency);
        if (inputs != null) {
            return copy(inputs);
        }
        final LoanInputs inrDefault = this.defaults.get(SupportedCurrency.INR);
        return inrDefault == null ? fallback() : copy(inrDefault);
    }

    private static LoanInputs toInputs(JsonNode node) {
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

    private static PrepaymentFrequency readFrequency(JsonNode node) {
        if (node == null || node.isNull()) {
            return PrepaymentFrequency.YEARLY;
        }
        try {
            return PrepaymentFrequency.valueOf(node.asString().toUpperCase());
        } catch (final IllegalArgumentException ignored) {
            return PrepaymentFrequency.YEARLY;
        }
    }

    private static BigDecimal bd(JsonNode node, String field) {
        final JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.asString());
    }

    private static Integer intField(JsonNode node, String field) {
        final JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return Integer.valueOf(value.asString());
    }

    private static LoanInputs copy(LoanInputs source) {
        return new LoanInputs(
                source.getLoanAmount(),
                source.getAnnualRatePct(),
                source.getTenureYears(),
                source.getTenureMonths(),
                source.getInflationRatePct(),
                source.getExtraPerPeriod(),
                source.getExtraFrequency(),
                source.getExtraEmisPerYear(),
                source.getEmiStepUpPct());
    }

    private static LoanInputs fallback() {
        final var inputs = new LoanInputs();
        inputs.setLoanAmount(BigDecimal.valueOf(5_000_000));
        inputs.setAnnualRatePct(BigDecimal.valueOf(8.5));
        inputs.setTenureYears(20);
        inputs.setTenureMonths(0);
        inputs.setInflationRatePct(BigDecimal.valueOf(6));
        inputs.setExtraPerPeriod(BigDecimal.ZERO);
        inputs.setExtraFrequency(PrepaymentFrequency.YEARLY);
        inputs.setExtraEmisPerYear(0);
        inputs.setEmiStepUpPct(BigDecimal.ZERO);
        return inputs;
    }
}
