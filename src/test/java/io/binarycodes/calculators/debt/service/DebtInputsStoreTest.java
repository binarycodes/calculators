package io.binarycodes.calculators.debt.service;

import io.binarycodes.calculators.debt.domain.Debt;
import io.binarycodes.calculators.debt.domain.DebtPlanInputs;
import io.binarycodes.calculators.debt.domain.PayoffStrategy;
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
    void round_trip_preserves_scalar_fields_and_the_debts_list() {
        final var card = new Debt("Credit card", new BigDecimal("250000"), new BigDecimal("36"),
                null, new BigDecimal("5"), null, null);
        final var loan = new Debt("Car loan", new BigDecimal("600000"), new BigDecimal("10"),
                new BigDecimal("12000"), null, new BigDecimal("0"), 6);
        final var inputs = new DebtPlanInputs(new ArrayList<>(List.of(card, loan)),
                new BigDecimal("10000"), PayoffStrategy.SNOWBALL, new BigDecimal("6"));

        final ObjectNode json = store.toJsonNode(inputs);
        final DebtPlanInputs restored = store.fromJsonNode(json);

        assertEquals(0, inputs.getExtraPerMonth().compareTo(restored.getExtraPerMonth()));
        assertEquals(PayoffStrategy.SNOWBALL, restored.getStrategy());
        assertEquals(0, inputs.getInflationRatePct().compareTo(restored.getInflationRatePct()));

        assertEquals(2, restored.getDebts().size());
        final Debt restoredCard = restored.getDebts().get(0);
        assertEquals("Credit card", restoredCard.getName());
        assertEquals(0, new BigDecimal("250000").compareTo(restoredCard.getBalance()));
        assertEquals(0, new BigDecimal("5").compareTo(restoredCard.getMinimumPct()));
        assertNull(restoredCard.getMinimumPayment());

        final Debt restoredLoan = restored.getDebts().get(1);
        assertEquals(0, new BigDecimal("12000").compareTo(restoredLoan.getMinimumPayment()));
        assertEquals(6, restoredLoan.getPromoMonths());
    }

    @Test
    void an_empty_debts_list_round_trips() {
        final var inputs = new DebtPlanInputs(new ArrayList<>(), new BigDecimal("100"),
                PayoffStrategy.AVALANCHE, null);
        final DebtPlanInputs restored = store.fromJsonNode(store.toJsonNode(inputs));
        assertTrue(restored.getDebts().isEmpty());
        assertNull(restored.getInflationRatePct());
    }

    @Test
    void a_fully_null_debt_survives_round_trip() {
        final var inputs = new DebtPlanInputs(new ArrayList<>(List.of(new Debt())), null, null, null);
        final DebtPlanInputs restored = store.fromJsonNode(store.toJsonNode(inputs));
        assertEquals(1, restored.getDebts().size());
        final Debt restored0 = restored.getDebts().get(0);
        assertNull(restored0.getName());
        assertNull(restored0.getBalance());
        assertNull(restored0.getPromoMonths());
        assertNull(restored.getStrategy());
    }
}
