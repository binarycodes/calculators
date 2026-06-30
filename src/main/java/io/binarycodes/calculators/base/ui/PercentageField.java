package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.data.value.ValueChangeMode;

/**
 * Factory for the percentage / rate number input shared across every calculator
 * form. Centralises the configuration so it lives in one place — in particular
 * it deliberately sets <b>no</b> numeric step: a step (e.g. {@code 0.1}) makes
 * the browser reject valid two-decimal rates like {@code 4.45%} as a step
 * mismatch, flagging the field invalid with no explanatory message. Step buttons
 * are hidden and value changes are reported lazily for live recalculation.
 */
public final class PercentageField {

    private PercentageField() {
    }

    public static NumberField create(String label) {
        final NumberField field = new NumberField(label);
        field.setStepButtonsVisible(false);
        field.setValueChangeMode(ValueChangeMode.LAZY);
        return field;
    }
}
