package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.data.value.ValueChangeMode;
import io.binarycodes.calculators.base.i18n.Translations;

/**
 * Factory for the percentage / rate number input shared across every calculator
 * form. Centralises the configuration so it lives in one place:
 *
 * <ul>
 *   <li>No numeric step — a step (e.g. {@code 0.1}) makes the browser reject
 *       valid two-decimal rates like {@code 4.45%} as a step mismatch.</li>
 *   <li>A bad-input error message — without one, an unparsable entry (e.g. the
 *       dangling {@code "4."}) leaves the field invalid with no explanation,
 *       since bad input is a field-level constraint the binder can't message.</li>
 *   <li>Hidden step buttons and lazy value changes for live recalculation.</li>
 * </ul>
 */
public final class PercentageField {

    private PercentageField() {
    }

    public static NumberField create(String label) {
        final NumberField field = new NumberField(label);
        field.setStepButtonsVisible(false);
        field.setValueChangeMode(ValueChangeMode.LAZY);
        field.setI18n(new NumberField.NumberFieldI18n()
                .setBadInputErrorMessage(Translations.get("validation.number")));
        return field;
    }
}
