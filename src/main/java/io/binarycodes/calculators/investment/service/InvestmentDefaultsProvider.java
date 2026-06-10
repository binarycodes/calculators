package io.binarycodes.calculators.investment.service;

import io.binarycodes.calculators.base.common.TimeHorizonMode;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.investment.domain.ContributionFrequency;
import io.binarycodes.calculators.investment.domain.InvestmentInputs;
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
 * Loads {@code investment-defaults.json} from classpath into typed
 * {@link InvestmentInputs} per currency.
 */
@Service
public class InvestmentDefaultsProvider {

    private final Resource defaultsResource;
    private final Map<SupportedCurrency, InvestmentInputs> defaults = new EnumMap<>(SupportedCurrency.class);

    public InvestmentDefaultsProvider(@Value("classpath:investment-defaults.json") Resource defaultsResource) {
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

    public InvestmentInputs forCurrency(SupportedCurrency currency) {
        final InvestmentInputs inputs = this.defaults.get(currency);
        if (inputs != null) {
            return copy(inputs);
        }
        final InvestmentInputs inrDefault = this.defaults.get(SupportedCurrency.INR);
        return inrDefault == null ? fallback() : copy(inrDefault);
    }

    private static InvestmentInputs toInputs(JsonNode node) {
        final var inputs = new InvestmentInputs();
        inputs.setAmount(bd(node, "amount"));
        inputs.setFrequency(readFrequency(node.get("frequency")));
        inputs.setGrowthRatePct(bd(node, "growthRate"));
        inputs.setTaxRatePct(bd(node, "taxRate"));
        inputs.setInflationRatePct(bd(node, "inflationRate"));
        inputs.setStepUpPct(bd(node, "stepUp"));
        inputs.setHorizonMode(readMode(node.get("horizonMode")));
        inputs.setInvestYears(intField(node, "investYears"));
        inputs.setInvestMonths(intField(node, "investMonths"));
        inputs.setCurrentAge(intField(node, "currentAge"));
        inputs.setGoalAge(intField(node, "goalAge"));
        inputs.setTargetYear(intField(node, "targetYear"));
        inputs.setTargetMonth(intField(node, "targetMonth"));
        inputs.setHoldYears(intField(node, "holdYears"));
        inputs.setHoldMonths(intField(node, "holdMonths"));
        return inputs;
    }

    private static ContributionFrequency readFrequency(JsonNode node) {
        if (node == null || node.isNull()) {
            return ContributionFrequency.MONTHLY;
        }
        try {
            return ContributionFrequency.valueOf(node.asText().toUpperCase());
        } catch (final IllegalArgumentException ignored) {
            return ContributionFrequency.MONTHLY;
        }
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

    private static BigDecimal bd(JsonNode node, String field) {
        final JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.asText());
    }

    private static Integer intField(JsonNode node, String field) {
        final JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return Integer.valueOf(value.asText());
    }

    private static InvestmentInputs copy(InvestmentInputs source) {
        return new InvestmentInputs(
                source.getAmount(),
                source.getFrequency(),
                source.getGrowthRatePct(),
                source.getTaxRatePct(),
                source.getInflationRatePct(),
                source.getStepUpPct(),
                source.getHorizonMode(),
                source.getInvestYears(),
                source.getInvestMonths(),
                source.getCurrentAge(),
                source.getGoalAge(),
                source.getTargetYear(),
                source.getTargetMonth(),
                source.getHoldYears(),
                source.getHoldMonths());
    }

    private static InvestmentInputs fallback() {
        final var inputs = new InvestmentInputs();
        inputs.setAmount(BigDecimal.valueOf(25_000));
        inputs.setFrequency(ContributionFrequency.MONTHLY);
        inputs.setGrowthRatePct(BigDecimal.valueOf(12));
        inputs.setTaxRatePct(BigDecimal.ZERO);
        inputs.setInflationRatePct(BigDecimal.valueOf(6));
        inputs.setStepUpPct(BigDecimal.ZERO);
        inputs.setHorizonMode(TimeHorizonMode.YEARS);
        inputs.setInvestYears(15);
        inputs.setInvestMonths(0);
        inputs.setCurrentAge(35);
        inputs.setGoalAge(50);
        inputs.setTargetYear(java.time.Year.now().getValue() + 15);
        inputs.setTargetMonth(12);
        inputs.setHoldYears(0);
        inputs.setHoldMonths(0);
        return inputs;
    }
}
