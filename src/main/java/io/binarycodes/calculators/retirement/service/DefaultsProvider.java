package io.binarycodes.calculators.retirement.service;

import io.binarycodes.calculators.base.common.CalculatorDefaults;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.base.common.Frequency;
import io.binarycodes.calculators.retirement.domain.Contribution;
import io.binarycodes.calculators.retirement.domain.FutureExpense;
import io.binarycodes.calculators.retirement.domain.FutureIncome;
import io.binarycodes.calculators.retirement.domain.RecurringExpense;
import io.binarycodes.calculators.retirement.domain.RecurringIncome;
import io.binarycodes.calculators.retirement.domain.RetirementBenefit;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;
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
 * Loads {@code defaults.json} from classpath into typed
 * {@link RetirementInputs} per currency. JSON shape mirrors the legacy file
 * — string-encoded numbers, keyed by currency code.
 */
@Service
public class DefaultsProvider implements CalculatorDefaults<RetirementInputs> {

    private final Resource defaultsResource;
    private final Map<SupportedCurrency, RetirementInputs> defaults = new EnumMap<>(SupportedCurrency.class);

    public DefaultsProvider(@Value("classpath:defaults.json") Resource defaultsResource) {
        this.defaultsResource = defaultsResource;
    }

    @PostConstruct
    void load() throws IOException {
        final ObjectMapper om = JsonMapper.builder().build();
        try (InputStream in = this.defaultsResource.getInputStream()) {
            final JsonNode root = om.readTree(in);
            for (final SupportedCurrency c : SupportedCurrency.values()) {
                final JsonNode node = root.get(c.name());
                if (node == null) {
                    continue;
                }
                this.defaults.put(c, toInputs(node));
            }
        }
    }

    public RetirementInputs forCurrency(SupportedCurrency c) {
        final RetirementInputs in = this.defaults.get(c);
        if (in != null) {
            return in;
        }
        // Fall back to INR if a currency entry is missing.
        return this.defaults.getOrDefault(SupportedCurrency.INR, fallback());
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
        inputs.setCorpusTaxRatePct(bd(n, "corpusTaxRate"));
        inputs.setPreRetirementContributions(readContributions(n.get("preRetirementContributions")));
        inputs.setPostRetirementContributions(readContributions(n.get("postRetirementContributions")));
        inputs.setFutureExpenses(readFutureExpenses(n.get("futureExpenses")));
        inputs.setRetirementBenefits(readRetirementBenefits(n.get("retirementBenefits")));
        inputs.setFutureIncomes(readFutureIncomes(n.get("futureIncomes")));
        inputs.setRecurringExpenses(readRecurringExpenses(n.get("recurringExpenses")));
        inputs.setRecurringIncomes(readRecurringIncomes(n.get("recurringIncomes")));
        return inputs;
    }

    private static List<Contribution> readContributions(JsonNode arrayNode) {
        final List<Contribution> out = new ArrayList<>();
        if (arrayNode == null || !arrayNode.isArray()) {
            return out;
        }
        for (final JsonNode entry : arrayNode) {
            final var contribution = new Contribution();
            contribution.setAmount(bd(entry, "amount"));
            contribution.setFrequency(readFrequency(entry.get("frequency")));
            contribution.setGrowthPct(bd(entry, "growth"));
            contribution.setStepUpPct(bd(entry, "stepUp"));
            contribution.setTaxRatePct(bd(entry, "taxRate"));
            out.add(contribution);
        }
        return out;
    }

    private static List<RecurringExpense> readRecurringExpenses(JsonNode arrayNode) {
        final List<RecurringExpense> out = new ArrayList<>();
        if (arrayNode == null || !arrayNode.isArray()) {
            return out;
        }
        for (final JsonNode entry : arrayNode) {
            final var expense = new RecurringExpense();
            if (entry.has("year") && !entry.get("year").isNull()) {
                expense.setYear(entry.get("year").asInt());
            }
            if (entry.has("stopYear") && !entry.get("stopYear").isNull()) {
                expense.setStopYear(entry.get("stopYear").asInt());
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
                income.setYear(entry.get("year").asInt());
            }
            if (entry.has("stopYear") && !entry.get("stopYear").isNull()) {
                income.setStopYear(entry.get("stopYear").asInt());
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
                income.setYear(entry.get("year").asInt());
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
                expense.setYear(entry.get("year").asInt());
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

    private static RetirementInputs fallback() {
        final var inputs = new RetirementInputs();
        inputs.setCurrentAge(35);
        inputs.setRetireAge(60);
        inputs.setLifeExp(90);
        inputs.setCorpus(BigDecimal.valueOf(5_000_000));
        inputs.setMonthlyExpenses(BigDecimal.valueOf(50_000));
        inputs.setInflationPct(BigDecimal.valueOf(6));
        inputs.setGrowthPrePct(BigDecimal.valueOf(12));
        inputs.setGrowthPostPct(BigDecimal.valueOf(8));
        inputs.setCorpusTaxRatePct(BigDecimal.ZERO);
        inputs.setPreRetirementContributions(new ArrayList<>(List.of(new Contribution(
                BigDecimal.valueOf(25_000), Frequency.MONTHLY,
                BigDecimal.valueOf(12), BigDecimal.ZERO, BigDecimal.ZERO))));
        inputs.setPostRetirementContributions(new ArrayList<>());
        return inputs;
    }
}
