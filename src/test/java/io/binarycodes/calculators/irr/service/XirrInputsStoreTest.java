package io.binarycodes.calculators.irr.service;

import io.binarycodes.calculators.base.common.ScenarioCodec;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.base.common.Frequency;
import io.binarycodes.calculators.irr.domain.DatedCashflow;
import io.binarycodes.calculators.irr.domain.RecurringCashflow;
import io.binarycodes.calculators.irr.domain.XirrInputs;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XirrInputsStoreTest {

    private final XirrInputsStore store = new XirrInputsStore();

    @Test
    void round_trip_preserves_one_off_and_recurring_entries() {
        final var inputs = new XirrInputs();
        inputs.setOneOffInvestments(List.of(
                new DatedCashflow(LocalDate.parse("2021-03-15"), "Lump sum", new BigDecimal("50000"))));
        inputs.setRecurringInvestments(List.of(
                new RecurringCashflow(LocalDate.parse("2021-01-01"), Frequency.QUARTERLY, 8, "Premium",
                        new BigDecimal("10000"))));
        inputs.setOneOffWithdrawals(List.of(
                new DatedCashflow(LocalDate.parse("2025-01-01"), "Maturity", new BigDecimal("250000"))));

        final XirrInputs restored = store.fromJsonNode(store.toJsonNode(inputs));

        final DatedCashflow restoredOneOff = restored.getOneOffInvestments().get(0);
        assertEquals(LocalDate.parse("2021-03-15"), restoredOneOff.getDate());
        assertEquals("Lump sum", restoredOneOff.getDescription());
        assertEquals(0, restoredOneOff.getAmount().compareTo(new BigDecimal("50000")));

        final RecurringCashflow restoredRecurring = restored.getRecurringInvestments().get(0);
        assertEquals(LocalDate.parse("2021-01-01"), restoredRecurring.getStartDate());
        assertEquals(Frequency.QUARTERLY, restoredRecurring.getFrequency());
        assertEquals(8, restoredRecurring.getCount());
        assertEquals(0, restoredRecurring.getAmount().compareTo(new BigDecimal("10000")));

        assertEquals(1, restored.getOneOffWithdrawals().size());
        assertTrue(restored.getRecurringWithdrawals().isEmpty());
    }

    @Test
    void blank_date_and_amount_survive_as_null() {
        final var inputs = new XirrInputs();
        inputs.setOneOffInvestments(List.of(new DatedCashflow()));

        final DatedCashflow restored = store.fromJsonNode(store.toJsonNode(inputs)).getOneOffInvestments().get(0);

        assertNull(restored.getDate());
        assertNull(restored.getAmount());
    }

    @Test
    void survives_a_full_share_link_round_trip() {
        final var inputs = new XirrInputs();
        inputs.setRecurringInvestments(List.of(
                new RecurringCashflow(LocalDate.parse("2023-07-01"), Frequency.MONTHLY, 36, "SIP",
                        new BigDecimal("25000"))));
        inputs.setOneOffWithdrawals(List.of(
                new DatedCashflow(LocalDate.parse("2026-07-01"), "Current value", new BigDecimal("1100000"))));

        // Param generation: inputs → JSON → encoded token.
        final String token = ScenarioCodec.encode(SupportedCurrency.INR, store.toJsonNode(inputs));
        // Param-to-bean: token → decoded JSON → inputs bean.
        final XirrInputs restored = store.fromJsonNode(ScenarioCodec.decode(token).inputs());

        final RecurringCashflow recurring = restored.getRecurringInvestments().get(0);
        assertEquals(LocalDate.parse("2023-07-01"), recurring.getStartDate());
        assertEquals(36, recurring.getCount());
        assertEquals(0, recurring.getAmount().compareTo(new BigDecimal("25000")));
        final DatedCashflow withdrawal = restored.getOneOffWithdrawals().get(0);
        assertEquals(LocalDate.parse("2026-07-01"), withdrawal.getDate());
        assertEquals(0, withdrawal.getAmount().compareTo(new BigDecimal("1100000")));
    }

    @Test
    void invalid_frequency_string_falls_back_to_monthly() {
        final ObjectNode root = JsonMapper.builder().build().createObjectNode();
        final var recurring = root.putArray("recurringInvestments").addObject();
        recurring.put("frequency", "FORTNIGHTLY");
        recurring.put("count", "3");
        recurring.put("amount", "100");

        assertEquals(Frequency.MONTHLY,
                store.fromJsonNode(root).getRecurringInvestments().get(0).getFrequency());
    }
}
