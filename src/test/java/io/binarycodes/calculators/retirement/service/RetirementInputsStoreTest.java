package io.binarycodes.calculators.retirement.service;

import io.binarycodes.calculators.base.common.Frequency;
import io.binarycodes.calculators.retirement.domain.FutureExpense;
import io.binarycodes.calculators.retirement.domain.FutureIncome;
import io.binarycodes.calculators.retirement.domain.RecurringExpense;
import io.binarycodes.calculators.retirement.domain.RecurringIncome;
import io.binarycodes.calculators.retirement.domain.RetirementBenefit;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetirementInputsStoreTest {

    private final RetirementInputsStore store = new RetirementInputsStore();

    private static RetirementInputs baseInputs() {
        final var inputs = new RetirementInputs();
        inputs.setCurrentAge(35);
        inputs.setRetireAge(60);
        inputs.setLifeExp(85);
        inputs.setCorpus(new BigDecimal("5000000"));
        inputs.setMonthlyExpenses(new BigDecimal("50000"));
        inputs.setInflationPct(new BigDecimal("6"));
        inputs.setGrowthPrePct(new BigDecimal("12"));
        inputs.setGrowthPostPct(new BigDecimal("8"));
        inputs.setCorpusTaxRatePct(new BigDecimal("10"));
        inputs.setMonthlyInvPre(new BigDecimal("30000"));
        inputs.setSipGrowthPrePct(new BigDecimal("12"));
        inputs.setSipStepUpPrePct(new BigDecimal("5"));
        inputs.setTaxRatePrePct(new BigDecimal("15"));
        inputs.setMonthlyInvPost(BigDecimal.ZERO);
        inputs.setSipGrowthPostPct(BigDecimal.ZERO);
        inputs.setSipStepUpPostPct(BigDecimal.ZERO);
        inputs.setTaxRatePostPct(BigDecimal.ZERO);
        inputs.setFutureExpenses(List.of());
        inputs.setRetirementBenefits(List.of());
        inputs.setFutureIncomes(List.of());
        inputs.setRecurringExpenses(List.of());
        inputs.setRecurringIncomes(List.of());
        return inputs;
    }

    @Test
    void round_trip_preserves_scalar_fields() {
        final RetirementInputs inputs = baseInputs();
        final ObjectNode json = store.toJsonNode(inputs);
        final RetirementInputs restored = store.fromJsonNode(json);

        assertEquals(35, restored.getCurrentAge());
        assertEquals(60, restored.getRetireAge());
        assertEquals(85, restored.getLifeExp());
        assertEquals(0, inputs.getCorpus().compareTo(restored.getCorpus()));
        assertEquals(0, inputs.getMonthlyExpenses().compareTo(restored.getMonthlyExpenses()));
        assertEquals(0, inputs.getInflationPct().compareTo(restored.getInflationPct()));
        assertEquals(0, inputs.getGrowthPrePct().compareTo(restored.getGrowthPrePct()));
        assertEquals(0, inputs.getCorpusTaxRatePct().compareTo(restored.getCorpusTaxRatePct()));
        assertEquals(0, inputs.getTaxRatePrePct().compareTo(restored.getTaxRatePrePct()));
    }

    @Test
    void round_trip_preserves_all_five_list_types() {
        final RetirementInputs inputs = baseInputs();

        final var futureExpense = new FutureExpense();
        futureExpense.setYear(2030);
        futureExpense.setDescription("Car purchase");
        futureExpense.setAmount(new BigDecimal("800000"));
        futureExpense.setInflationPct(new BigDecimal("4"));
        inputs.setFutureExpenses(List.of(futureExpense));

        final var benefit = new RetirementBenefit();
        benefit.setDescription("Pension");
        benefit.setAmount(new BigDecimal("20000"));
        benefit.setTaxRatePct(new BigDecimal("10"));
        inputs.setRetirementBenefits(List.of(benefit));

        final var futureIncome = new FutureIncome();
        futureIncome.setYear(2028);
        futureIncome.setDescription("Rental income");
        futureIncome.setAmount(new BigDecimal("15000"));
        futureIncome.setTaxRatePct(new BigDecimal("20"));
        inputs.setFutureIncomes(List.of(futureIncome));

        final var recurringExpense = new RecurringExpense();
        recurringExpense.setYear(2026);
        recurringExpense.setStopYear(2040);
        recurringExpense.setDescription("School fees");
        recurringExpense.setFrequency(Frequency.YEARLY);
        recurringExpense.setAmount(new BigDecimal("120000"));
        recurringExpense.setInflationPct(new BigDecimal("5"));
        inputs.setRecurringExpenses(List.of(recurringExpense));

        final var recurringIncome = new RecurringIncome();
        recurringIncome.setYear(2025);
        recurringIncome.setStopYear(2035);
        recurringIncome.setDescription("Freelance");
        recurringIncome.setFrequency(Frequency.MONTHLY);
        recurringIncome.setAmount(new BigDecimal("50000"));
        recurringIncome.setTaxRatePct(new BigDecimal("15"));
        inputs.setRecurringIncomes(List.of(recurringIncome));

        final ObjectNode json = store.toJsonNode(inputs);
        final RetirementInputs restored = store.fromJsonNode(json);

        final FutureExpense restoredFE = restored.getFutureExpenses().getFirst();
        assertEquals(2030, restoredFE.getYear());
        assertEquals("Car purchase", restoredFE.getDescription());
        assertEquals(0, futureExpense.getAmount().compareTo(restoredFE.getAmount()));
        assertEquals(0, futureExpense.getInflationPct().compareTo(restoredFE.getInflationPct()));

        final RetirementBenefit restoredBenefit = restored.getRetirementBenefits().getFirst();
        assertEquals("Pension", restoredBenefit.getDescription());
        assertEquals(0, benefit.getAmount().compareTo(restoredBenefit.getAmount()));
        assertEquals(0, benefit.getTaxRatePct().compareTo(restoredBenefit.getTaxRatePct()));

        final FutureIncome restoredFI = restored.getFutureIncomes().getFirst();
        assertEquals(2028, restoredFI.getYear());
        assertEquals(0, futureIncome.getAmount().compareTo(restoredFI.getAmount()));
        assertEquals(0, futureIncome.getTaxRatePct().compareTo(restoredFI.getTaxRatePct()));

        final RecurringExpense restoredRE = restored.getRecurringExpenses().getFirst();
        assertEquals(2026, restoredRE.getYear());
        assertEquals(2040, restoredRE.getStopYear());
        assertEquals(Frequency.YEARLY, restoredRE.getFrequency());
        assertEquals(0, recurringExpense.getAmount().compareTo(restoredRE.getAmount()));
        assertEquals(0, recurringExpense.getInflationPct().compareTo(restoredRE.getInflationPct()));

        final RecurringIncome restoredRI = restored.getRecurringIncomes().getFirst();
        assertEquals(2025, restoredRI.getYear());
        assertEquals(2035, restoredRI.getStopYear());
        assertEquals(Frequency.MONTHLY, restoredRI.getFrequency());
        assertEquals(0, recurringIncome.getAmount().compareTo(restoredRI.getAmount()));
        assertEquals(0, recurringIncome.getTaxRatePct().compareTo(restoredRI.getTaxRatePct()));
    }

    @Test
    void null_frequency_in_recurring_entry_defaults_to_monthly() {
        final RetirementInputs inputs = baseInputs();
        final var expense = new RecurringExpense();
        expense.setYear(2026);
        expense.setAmount(BigDecimal.valueOf(10_000));
        expense.setFrequency(null);
        inputs.setRecurringExpenses(List.of(expense));

        final ObjectNode json = store.toJsonNode(inputs);
        assertEquals(Frequency.MONTHLY, store.fromJsonNode(json).getRecurringExpenses().getFirst().getFrequency());
    }

    @Test
    void recurring_expense_null_inflation_pct_round_trips_as_null() {
        // RecurringExpense.inflationPct uses bdOrNull() — blank/null persists as null, not ZERO.
        final RetirementInputs inputs = baseInputs();
        final var expense = new RecurringExpense();
        expense.setYear(2026);
        expense.setAmount(BigDecimal.valueOf(10_000));
        expense.setFrequency(Frequency.MONTHLY);
        expense.setInflationPct(null);
        inputs.setRecurringExpenses(List.of(expense));

        final ObjectNode json = store.toJsonNode(inputs);
        assertNull(store.fromJsonNode(json).getRecurringExpenses().getFirst().getInflationPct());
    }

    @Test
    void null_list_fields_serialize_as_empty_arrays_and_restore_as_empty_lists() {
        final RetirementInputs inputs = baseInputs();
        inputs.setFutureExpenses(null);
        inputs.setRetirementBenefits(null);
        inputs.setFutureIncomes(null);
        inputs.setRecurringExpenses(null);
        inputs.setRecurringIncomes(null);

        final ObjectNode json = store.toJsonNode(inputs);
        final RetirementInputs restored = store.fromJsonNode(json);

        assertNotNull(restored.getFutureExpenses());
        assertTrue(restored.getFutureExpenses().isEmpty());
        assertNotNull(restored.getRetirementBenefits());
        assertTrue(restored.getRetirementBenefits().isEmpty());
        assertNotNull(restored.getFutureIncomes());
        assertTrue(restored.getFutureIncomes().isEmpty());
        assertNotNull(restored.getRecurringExpenses());
        assertTrue(restored.getRecurringExpenses().isEmpty());
        assertNotNull(restored.getRecurringIncomes());
        assertTrue(restored.getRecurringIncomes().isEmpty());
    }
}
