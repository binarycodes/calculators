package io.binarycodes.calculators.debt.ui;

import com.vaadin.browserless.BrowserlessExtension;
import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.flow.component.UI;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.debt.domain.Debt;
import io.binarycodes.calculators.debt.domain.DebtPlanInputs;
import io.binarycodes.calculators.debt.domain.PayoffStrategy;
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
        return new DebtPlanInputs(new ArrayList<>(List.of(card, loan)),
                new BigDecimal("10000"), PayoffStrategy.SNOWBALL, new BigDecimal("6"));
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
