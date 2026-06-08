package io.binarycodes.calculators.goal.service;

import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.goal.domain.GoalInputs;
import io.binarycodes.calculators.goal.domain.Investment;
import io.binarycodes.calculators.goal.domain.TimeHorizonMode;
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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Loads {@code goal-defaults.json} from classpath into typed {@link GoalInputs}
 * per currency. Each currency carries a top-level set of horizon fields plus
 * an {@code investments} array — at least one bucket per currency.
 */
@Service
public class GoalDefaultsProvider {

    private final Resource defaultsResource;
    private final Map<SupportedCurrency, GoalInputs> defaults = new EnumMap<>(SupportedCurrency.class);

    public GoalDefaultsProvider(@Value("classpath:goal-defaults.json") Resource defaultsResource) {
        this.defaultsResource = defaultsResource;
    }

    @PostConstruct
    void load() throws IOException {
        final ObjectMapper objectMapper = JsonMapper.builder().build();
        try (InputStream stream = this.defaultsResource.getInputStream()) {
            final JsonNode root = objectMapper.readTree(stream);
            for (final SupportedCurrency currency : SupportedCurrency.values()) {
                final JsonNode node = root.get(currency.name());
                if (node == null) {
                    continue;
                }
                this.defaults.put(currency, toInputs(node));
            }
        }
    }

    public GoalInputs forCurrency(SupportedCurrency currency) {
        final GoalInputs inputs = this.defaults.get(currency);
        if (inputs != null) {
            return copy(inputs);
        }
        final GoalInputs inrDefault = this.defaults.get(SupportedCurrency.INR);
        return inrDefault == null ? fallback() : copy(inrDefault);
    }

    private static GoalInputs toInputs(JsonNode node) {
        final var inputs = new GoalInputs();
        inputs.setGoalAmount(bd(node, "goalAmount"));
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
                investment.setLabel(entry.get("label").asText());
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

    private static GoalInputs copy(GoalInputs source) {
        final List<Investment> copiedInvestments = new ArrayList<>();
        if (source.getInvestments() != null) {
            for (final Investment original : source.getInvestments()) {
                copiedInvestments.add(new Investment(
                        original.getLabel(),
                        original.getCurrentCorpus(),
                        original.getGrowthRatePct(),
                        original.getWithdrawalTaxRatePct(),
                        original.getAllocationPct(),
                        original.getStepUpPct()));
            }
        }
        return new GoalInputs(
                source.getGoalAmount(),
                copiedInvestments,
                source.getHorizonMode(),
                source.getYearsToGoal(),
                source.getMonthsToGoal(),
                source.getCurrentAge(),
                source.getGoalAge(),
                source.getTargetYear(),
                source.getTargetMonth());
    }

    private static GoalInputs fallback() {
        final var inputs = new GoalInputs();
        inputs.setGoalAmount(BigDecimal.valueOf(10_000_000));
        inputs.setHorizonMode(TimeHorizonMode.YEARS);
        inputs.setYearsToGoal(15);
        inputs.setMonthsToGoal(0);
        inputs.setCurrentAge(35);
        inputs.setGoalAge(50);
        inputs.setTargetYear(java.time.Year.now().getValue() + 15);
        inputs.setTargetMonth(12);
        final List<Investment> seed = new ArrayList<>();
        seed.add(new Investment("Default",
                BigDecimal.ZERO, BigDecimal.valueOf(12), BigDecimal.ZERO,
                BigDecimal.valueOf(100), BigDecimal.ZERO));
        inputs.setInvestments(seed);
        return inputs;
    }
}
