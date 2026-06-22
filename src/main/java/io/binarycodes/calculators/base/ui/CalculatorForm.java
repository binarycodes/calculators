package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.signals.Signal;

/**
 * The minimal contract {@link BaseCalculatorView} needs from a calculator's
 * input form: read/write the bound bean and observe field changes. Validation
 * and any form-specific extras stay on the concrete form classes.
 *
 * @param <I> the calculator's input bean type
 */
public interface CalculatorForm<I> {

    void setInputs(I inputs);

    /** Blank every field and drop optional rows — an empty form, not the defaults. */
    void clear();

    /**
     * Refresh the per-card validation messages (top-right of each form card):
     * a generic note on cards with invalid fields, plus any card-level rules.
     * {@code calculationError} (nullable) is a whole-calculation failure to
     * attribute to the form's primary input card. The default marks every
     * {@link FormCard} whose own fields are invalid; forms with card-level rules
     * or a calculator error to surface override this and add to it.
     */
    default void showValidationMessages(String calculationError) {
        if (this instanceof Component component) {
            FormCard.refreshGenericErrors(component);
        }
    }

    I getInputs();

    /** A signal that fires whenever any bound field changes, for live recalculation. */
    Signal<I> inputsSignal();
}
