package io.binarycodes.calculators.goal.ui;

import com.vaadin.browserless.BrowserlessExtension;
import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import io.binarycodes.calculators.base.common.TimeHorizonMode;
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.goal.domain.GoalInputs;
import io.binarycodes.calculators.goal.domain.Investment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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

        // A class literal can't carry type arguments, so find() hands back a raw
        // RadioButtonGroup; this test() overload takes the raw group plus the value
        // type and gives back a typed tester that also selects by visible label.
        final var horizonMode = test(find(RadioButtonGroup.class, form).single(), TimeHorizonMode.class);

        horizonMode.selectItem(Translations.get("timeHorizon.years"));
        roundTrip();
        assertEquals(1, find(IntegerField.class, form)
                .withCaption(Translations.get("field.years")).all().size());
        assertEquals(1, find(IntegerField.class, form)
                .withCaption(Translations.get("field.months")).all().size());

        horizonMode.selectItem(Translations.get("timeHorizon.ages"));
        roundTrip();
        assertEquals(2, find(IntegerField.class, form)
                        .withCaption(Translations.get("field.currentAge")).all().size() +
                        find(IntegerField.class, form)
                                .withCaption(Translations.get("field.goalAge")).all().size(),
                "AGES mode shows Current Age and Goal Age fields");

        horizonMode.selectItem(Translations.get("timeHorizon.targetYear"));
        roundTrip();
        assertEquals(1, find(IntegerField.class, form)
                .withCaption(Translations.get("field.targetYear")).all().size());
        assertEquals(1, find(Select.class, form)
                .withCaption(Translations.get("field.targetMonth")).all().size());
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
                roundTripped.getInvestments().getFirst().getAllocationPct()));
        assertEquals(0, new BigDecimal("5").compareTo(
                        roundTripped.getInvestments().getFirst().getStepUpPct()),
                "per-row step-up must round-trip");
    }
}
