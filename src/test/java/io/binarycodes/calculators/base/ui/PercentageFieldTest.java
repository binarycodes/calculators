package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.component.textfield.NumberField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The shared percentage-field factory feeds every calculator's rate inputs, so
 * guarding it here covers them all: it must impose no numeric step (a step would
 * flag valid two-decimal rates like 4.45% invalid as a step mismatch) and hide
 * the step buttons.
 */
class PercentageFieldTest {

    @Test
    void create_imposes_no_step_so_decimal_rates_are_valid() {
        final NumberField field = PercentageField.create("Rate");
        assertNull(field.getElement().getProperty("step"),
                "percentage fields must not set a step (it would reject decimals like 4.45%)");
        assertFalse(field.isStepButtonsVisible(), "step buttons should be hidden");
    }

    @Test
    void create_configures_a_bad_input_message_so_unparsable_entries_explain_themselves() {
        final NumberField field = PercentageField.create("Rate");
        assertNotNull(field.getI18n(), "i18n must be set so bad input shows a message");
        final String badInput = field.getI18n().getBadInputErrorMessage();
        assertNotNull(badInput, "bad-input error message must be configured");
        assertFalse(badInput.isBlank(), "bad-input error message must not be blank");
    }
}
