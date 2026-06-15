package io.binarycodes.calculators.base.ui;

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

    I getInputs();

    /** A signal that fires whenever any bound field changes, for live recalculation. */
    Signal<I> inputsSignal();
}
