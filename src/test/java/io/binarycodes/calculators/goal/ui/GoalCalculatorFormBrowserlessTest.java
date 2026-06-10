package io.binarycodes.calculators.goal.ui;

import com.vaadin.browserless.BrowserlessExtension;
import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.goal.domain.GoalInputs;
import io.binarycodes.calculators.goal.domain.Investment;
import io.binarycodes.calculators.base.common.TimeHorizonMode;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Smoke test for the {@link GoalCalculatorForm}: the horizon toggle swaps the
 * visible sub-field set, and field values round-trip cleanly through the
 * binder.
 */
class GoalCalculatorFormBrowserlessTest extends BrowserlessTest {

    @RegisterExtension
    static final BrowserlessExtension EXTENSION = new BrowserlessExtension()
            .withServices(UserPreferences.class);

    @Override
    protected Set<String> scanPackages() {
        // Skip route discovery so the @Route("") landing view isn't mounted
        // (its constructor needs a UI for menu introspection).
        return Set.of("io.binarycodes.calculators.test.noroutes");
    }

    @Test
    void switching_horizon_mode_swaps_visible_fields() {
        final var form = new GoalCalculatorForm(new UserPreferences());
        UI.getCurrent().add(form);
        roundTrip();

        final var group = find(RadioButtonGroup.class, form).single();

        group.setValue(TimeHorizonMode.YEARS);
        roundTrip();
        assertEquals(1, find(IntegerField.class, form).withCaption("Years").all().size());
        assertEquals(1, find(IntegerField.class, form).withCaption("Months").all().size());

        group.setValue(TimeHorizonMode.AGES);
        roundTrip();
        assertEquals(2, find(IntegerField.class, form)
                .withCaption("Current Age").all().size() +
                find(IntegerField.class, form)
                        .withCaption("Goal Age").all().size(),
                "AGES mode shows Current Age and Goal Age fields");

        group.setValue(TimeHorizonMode.TARGET_YEAR);
        roundTrip();
        assertEquals(1, find(IntegerField.class, form)
                .withCaption("Target Year").all().size());
        assertEquals(1, find(Select.class, form)
                .withCaption("Target Month").all().size());
    }

    @Test
    void values_round_trip_through_binder() {
        final var form = new GoalCalculatorForm(new UserPreferences());

        final GoalInputs initial = new GoalInputs();
        initial.setGoalAmount(new BigDecimal("10000000"));
        initial.setHorizonMode(TimeHorizonMode.YEARS);
        initial.setYearsToGoal(15);
        initial.setMonthsToGoal(6);
        final List<Investment> investments = new ArrayList<>();
        investments.add(new Investment("Sole",
                new BigDecimal("500000"), BigDecimal.valueOf(12),
                new BigDecimal("12.5"), BigDecimal.valueOf(100), BigDecimal.valueOf(5)));
        initial.setInvestments(investments);
        form.setInputs(initial);

        final GoalInputs roundTripped = form.getInputs();
        assertNotNull(roundTripped.getGoalAmount());
        assertEquals(0, initial.getGoalAmount().compareTo(roundTripped.getGoalAmount()));
        assertEquals(TimeHorizonMode.YEARS, roundTripped.getHorizonMode());
        assertEquals(Integer.valueOf(15), roundTripped.getYearsToGoal());
        assertEquals(Integer.valueOf(6), roundTripped.getMonthsToGoal());
        assertEquals(1, roundTripped.getInvestments().size());
        assertEquals(0, new BigDecimal("100").compareTo(
                roundTripped.getInvestments().get(0).getAllocationPct()));
        assertEquals(0, new BigDecimal("5").compareTo(
                roundTripped.getInvestments().get(0).getStepUpPct()),
                "per-row step-up must round-trip");
    }
}
