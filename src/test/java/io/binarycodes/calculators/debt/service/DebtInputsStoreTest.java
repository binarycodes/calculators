package io.binarycodes.calculators.debt.service;

import io.binarycodes.calculators.debt.domain.Debt;
import io.binarycodes.calculators.debt.domain.DebtPlanInputs;
import io.binarycodes.calculators.debt.domain.PayoffStrategy;
import io.binarycodes.calculators.debt.domain.Windfall;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebtInputsStoreTest {

    private final DebtInputsStore store = new DebtInputsStore();

    @Test
    void round_trip_preserves_scalars_the_debts_list_and_the_windfalls_list() {
        final var card = new Debt("Credit card", new BigDecimal("250000"), new BigDecimal("36"),
                null, new BigDecimal("5"), null, null);
        final var loan = new Debt("Car loan", new BigDecimal("600000"), new BigDecimal("10"),
                new BigDecimal("12000"), null, new BigDecimal("0"), 6);
        final var inputs = new DebtPlanInputs();
        inputs.setDebts(new ArrayList<>(List.of(card, loan)));
        inputs.setMonthlyBudget(new BigDecimal("45000"));
        inputs.setBudgetStepUpPct(new BigDecimal("5"));
        inputs.setDefaultFeePerMonth(new BigDecimal("600"));
        inputs.setWindfalls(new ArrayList<>(List.of(new Windfall(6, new BigDecimal("50000")))));
        inputs.setStrategy(PayoffStrategy.CUSTOM);
        inputs.setInflationRatePct(new BigDecimal("6"));

        final ObjectNode json = store.toJsonNode(inputs);
        final DebtPlanInputs restored = store.fromJsonNode(json);

        assertEquals(0, new BigDecimal("45000").compareTo(restored.getMonthlyBudget()));
        assertEquals(0, new BigDecimal("5").compareTo(restored.getBudgetStepUpPct()));
        assertEquals(0, new BigDecimal("600").compareTo(restored.getDefaultFeePerMonth()));
        assertEquals(PayoffStrategy.CUSTOM, restored.getStrategy());
        assertEquals(0, new BigDecimal("6").compareTo(restored.getInflationRatePct()));

        assertEquals(2, restored.getDebts().size());
        final Debt restoredCard = restored.getDebts().get(0);
        assertEquals("Credit card", restoredCard.getName());
        assertEquals(0, new BigDecimal("5").compareTo(restoredCard.getMinimumPct()));
        assertNull(restoredCard.getMinimumPayment());
        assertEquals(6, restored.getDebts().get(1).getPromoMonths());

        assertEquals(1, restored.getWindfalls().size());
        assertEquals(6, restored.getWindfalls().get(0).getMonth());
        assertEquals(0, new BigDecimal("50000").compareTo(restored.getWindfalls().get(0).getAmount()));
    }

    @Test
    void debt_order_is_preserved_for_the_custom_strategy() {
        final var inputs = new DebtPlanInputs();
        inputs.setStrategy(PayoffStrategy.CUSTOM);
        inputs.setDebts(new ArrayList<>(List.of(
                new Debt("second", new BigDecimal("200"), new BigDecimal("10"), null, null, null, null),
                new Debt("first", new BigDecimal("100"), new BigDecimal("20"), null, null, null, null))));

        final DebtPlanInputs restored = store.fromJsonNode(store.toJsonNode(inputs));
        assertEquals("second", restored.getDebts().get(0).getName());
        assertEquals("first", restored.getDebts().get(1).getName());
    }

    @Test
    void empty_lists_round_trip() {
        final var inputs = new DebtPlanInputs();
        inputs.setMonthlyBudget(new BigDecimal("100"));
        inputs.setStrategy(PayoffStrategy.AVALANCHE);
        final DebtPlanInputs restored = store.fromJsonNode(store.toJsonNode(inputs));
        assertTrue(restored.getDebts().isEmpty());
        assertTrue(restored.getWindfalls().isEmpty());
        assertNull(restored.getBudgetStepUpPct());
    }

    @Test
    void a_fully_null_debt_survives_round_trip() {
        final var inputs = new DebtPlanInputs();
        inputs.setDebts(new ArrayList<>(List.of(new Debt())));
        final DebtPlanInputs restored = store.fromJsonNode(store.toJsonNode(inputs));
        assertEquals(1, restored.getDebts().size());
        final Debt restored0 = restored.getDebts().get(0);
        assertNull(restored0.getName());
        assertNull(restored0.getBalance());
        assertNull(restored0.getPromoMonths());
        assertNull(restored.getStrategy());
    }
}
