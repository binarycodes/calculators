package io.binarycodes.calculators.debt.ui;

import com.vaadin.browserless.BrowserlessExtension;
import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.flow.component.UI;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.debt.domain.Debt;
import io.binarycodes.calculators.debt.domain.DebtPlanInputs;
import io.binarycodes.calculators.debt.domain.PayoffStrategy;
import io.binarycodes.calculators.debt.domain.Windfall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test that debt rows round-trip through the form's binder and that the
 * add/remove list wiring drives validity. Catches binding-level regressions the
 * calculator unit tests can't.
 */
class DebtCalculatorFormBrowserlessTest extends BrowserlessTest {

    @RegisterExtension
    static final BrowserlessExtension EXTENSION = new BrowserlessExtension()
            .withServices(UserPreferences.class);

    @Override
    protected Set<String> scanPackages() {
        return Set.of("io.binarycodes.calculators.test.noroutes");
    }

    private static DebtPlanInputs seeded() {
        final var card = new Debt("Credit card", new BigDecimal("250000"), new BigDecimal("36"),
                null, new BigDecimal("5"), null, null);
        final var loan = new Debt("Car loan", new BigDecimal("600000"), new BigDecimal("10"),
                new BigDecimal("12000"), null, new BigDecimal("0"), 6);
        final var inputs = new DebtPlanInputs();
        inputs.setDebts(new ArrayList<>(List.of(card, loan)));
        inputs.setExtraPerMonth(new BigDecimal("10000"));
        inputs.setExtraStepUpPct(new BigDecimal("5"));
        inputs.setWindfalls(new ArrayList<>(List.of(new Windfall(6, new BigDecimal("50000")))));
        inputs.setStrategy(PayoffStrategy.SNOWBALL);
        inputs.setInflationRatePct(new BigDecimal("6"));
        return inputs;
    }

    @Test
    void debt_rows_round_trip_through_the_binder() {
        final var form = new DebtCalculatorForm(new UserPreferences());
        UI.getCurrent().add(form);
        form.setInputs(seeded());
        roundTrip();

        final DebtPlanInputs roundTripped = form.getInputs();
        assertEquals(2, roundTripped.getDebts().size());
        assertEquals(PayoffStrategy.SNOWBALL, roundTripped.getStrategy());
        assertEquals(0, new BigDecimal("10000").compareTo(roundTripped.getExtraPerMonth()));

        final Debt card = roundTripped.getDebts().get(0);
        assertEquals("Credit card", card.getName());
        assertEquals(0, new BigDecimal("250000").compareTo(card.getBalance()));
        assertEquals(0, new BigDecimal("5").compareTo(card.getMinimumPct()));

        final Debt loan = roundTripped.getDebts().get(1);
        assertEquals(0, new BigDecimal("12000").compareTo(loan.getMinimumPayment()));
        assertEquals(6, loan.getPromoMonths());
    }

    @Test
    void step_up_and_windfall_rows_round_trip() {
        final var form = new DebtCalculatorForm(new UserPreferences());
        UI.getCurrent().add(form);
        form.setInputs(seeded());
        roundTrip();

        final DebtPlanInputs roundTripped = form.getInputs();
        assertEquals(0, new BigDecimal("5").compareTo(roundTripped.getExtraStepUpPct()));
        assertEquals(1, roundTripped.getWindfalls().size());
        assertEquals(6, roundTripped.getWindfalls().get(0).getMonth());
        assertEquals(0, new BigDecimal("50000").compareTo(roundTripped.getWindfalls().get(0).getAmount()));
    }

    @Test
    void the_strategy_description_tracks_the_selected_strategy() {
        final var form = new DebtCalculatorForm(new UserPreferences());
        UI.getCurrent().add(form);

        final DebtPlanInputs custom = seeded();
        custom.setStrategy(PayoffStrategy.CUSTOM);
        form.setInputs(custom);
        assertTrue(form.strategyDescriptionText().contains("reorder"),
                "the custom description should mention reordering");

        final DebtPlanInputs avalanche = seeded();
        avalanche.setStrategy(PayoffStrategy.AVALANCHE);
        form.setInputs(avalanche);
        assertTrue(form.strategyDescriptionText().contains("highest rate"),
                "the avalanche description should mention the highest rate");
    }

    @Test
    void an_empty_form_is_invalid_and_a_seeded_form_is_valid() {
        final var form = new DebtCalculatorForm(new UserPreferences());
        UI.getCurrent().add(form);
        form.clear();
        roundTrip();
        assertFalse(form.isValid(), "a form with no debts must be invalid");

        form.setInputs(seeded());
        roundTrip();
        assertTrue(form.isValid(), "a fully seeded form must be valid");
    }
}
