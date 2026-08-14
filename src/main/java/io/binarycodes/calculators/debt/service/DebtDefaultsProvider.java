package io.binarycodes.calculators.debt.service;

import io.binarycodes.calculators.base.common.CalculatorDefaults;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.debt.domain.Debt;
import io.binarycodes.calculators.debt.domain.DebtPlanInputs;
import io.binarycodes.calculators.debt.domain.PayoffStrategy;
import io.binarycodes.calculators.debt.domain.Windfall;
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
 * Loads {@code debt-defaults.json} from classpath into typed
 * {@link DebtPlanInputs} per currency. Also holds the per-currency default
 * minimum floor that backs any debt without its own {@code minimumPayment}; the
 * view passes {@link #minimumFloor(SupportedCurrency)} into the calculator.
 */
@Service
public class DebtDefaultsProvider implements CalculatorDefaults<DebtPlanInputs> {

    private static final BigDecimal FALLBACK_FLOOR = BigDecimal.valueOf(500);

    private final Resource defaultsResource;
    private final Map<SupportedCurrency, DebtPlanInputs> defaults = new EnumMap<>(SupportedCurrency.class);
    private final Map<SupportedCurrency, BigDecimal> floors = new EnumMap<>(SupportedCurrency.class);

    public DebtDefaultsProvider(@Value("classpath:debt-defaults.json") Resource defaultsResource) {
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
                    this.floors.put(currency, bd(node, "minimumFloor"));
                }
            }
        }
    }

    @Override
    public DebtPlanInputs forCurrency(SupportedCurrency currency) {
        final DebtPlanInputs inputs = this.defaults.get(currency);
        if (inputs != null) {
            return copy(inputs);
        }
        final DebtPlanInputs inrDefault = this.defaults.get(SupportedCurrency.INR);
        return inrDefault == null ? fallback() : copy(inrDefault);
    }

    /** The default minimum floor for the currency, used when a debt omits its own fixed minimum. */
    public BigDecimal minimumFloor(SupportedCurrency currency) {
        final BigDecimal floor = this.floors.get(currency);
        if (floor != null && floor.signum() > 0) {
            return floor;
        }
        final BigDecimal inrFloor = this.floors.get(SupportedCurrency.INR);
        return inrFloor != null && inrFloor.signum() > 0 ? inrFloor : FALLBACK_FLOOR;
    }

    private static DebtPlanInputs toInputs(JsonNode node) {
        final var inputs = new DebtPlanInputs();
        inputs.setMonthlyBudget(bd(node, "monthlyBudget"));
        inputs.setBudgetStepUpPct(bdOrNull(node, "budgetStepUpPct"));
        inputs.setDefaultFeePerMonth(bdOrNull(node, "defaultFeePerMonth"));
        inputs.setStrategy(readStrategy(node.get("strategy")));
        inputs.setInflationRatePct(bd(node, "inflationRatePct"));
        inputs.setDebts(readDebts(node.get("debts")));
        inputs.setWindfalls(readWindfalls(node.get("windfalls")));
        return inputs;
    }

    private static List<Windfall> readWindfalls(JsonNode arrayNode) {
        final List<Windfall> windfalls = new ArrayList<>();
        if (arrayNode == null || !arrayNode.isArray()) {
            return windfalls;
        }
        for (final JsonNode entry : arrayNode) {
            final Integer month = entry.get("month") == null ? null : Integer.valueOf(entry.get("month").asString());
            windfalls.add(new Windfall(month, bdOrNull(entry, "amount")));
        }
        return windfalls;
    }

    private static List<Debt> readDebts(JsonNode arrayNode) {
        final List<Debt> debts = new ArrayList<>();
        if (arrayNode == null || !arrayNode.isArray()) {
            return debts;
        }
        for (final JsonNode entry : arrayNode) {
            final var debt = new Debt();
            debt.setName(text(entry.get("name")));
            debt.setBalance(bd(entry, "balance"));
            debt.setAprPct(bd(entry, "aprPct"));
            debt.setMinimumPayment(bdOrNull(entry, "minimumPayment"));
            debt.setMinimumPct(bdOrNull(entry, "minimumPct"));
            debt.setPromoAprPct(bdOrNull(entry, "promoAprPct"));
            debt.setPromoMonths(intField(entry, "promoMonths"));
            debts.add(debt);
        }
        return debts;
    }

    private static PayoffStrategy readStrategy(JsonNode value) {
        if (value == null || value.isNull() || value.asString().isBlank()) {
            return PayoffStrategy.AVALANCHE;
        }
        return PayoffStrategy.valueOf(value.asString());
    }

    private static String text(JsonNode value) {
        return value == null || value.isNull() ? null : value.asString();
    }

    private static BigDecimal bd(JsonNode node, String field) {
        final JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.asString());
    }

    /** Optional decimal — absent/blank stays null so the debt's minimum stays unset. */
    private static BigDecimal bdOrNull(JsonNode node, String field) {
        final JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asString().isBlank()) {
            return null;
        }
        return new BigDecimal(value.asString());
    }

    private static Integer intField(JsonNode node, String field) {
        final JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asString().isBlank()) {
            return null;
        }
        return Integer.valueOf(value.asString());
    }

    private static DebtPlanInputs copy(DebtPlanInputs source) {
        final List<Debt> debtsCopy = new ArrayList<>();
        for (final Debt debt : source.getDebts()) {
            debtsCopy.add(new Debt(debt.getName(), debt.getBalance(), debt.getAprPct(),
                    debt.getMinimumPayment(), debt.getMinimumPct(), debt.getPromoAprPct(), debt.getPromoMonths()));
        }
        final List<Windfall> windfallsCopy = new ArrayList<>();
        for (final Windfall windfall : source.getWindfalls()) {
            windfallsCopy.add(new Windfall(windfall.getMonth(), windfall.getAmount()));
        }
        return new DebtPlanInputs(debtsCopy, source.getMonthlyBudget(), source.getBudgetStepUpPct(),
                source.getDefaultFeePerMonth(), windfallsCopy, source.getStrategy(), source.getInflationRatePct());
    }

    private static DebtPlanInputs fallback() {
        final var card = new Debt("Credit card", BigDecimal.valueOf(250_000), BigDecimal.valueOf(36),
                null, BigDecimal.valueOf(5), null, null);
        final var loan = new Debt("Personal loan", BigDecimal.valueOf(400_000), BigDecimal.valueOf(14),
                BigDecimal.valueOf(9_000), null, null, null);
        final List<Debt> debts = new ArrayList<>(List.of(card, loan));
        return new DebtPlanInputs(debts, BigDecimal.valueOf(45_000), null, null, new ArrayList<>(),
                PayoffStrategy.AVALANCHE, BigDecimal.valueOf(6));
    }
}
