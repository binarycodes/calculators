package io.binarycodes.calculators.base.common;

import io.binarycodes.calculators.base.money.SupportedCurrency;

/**
 * Supplies a calculator's default inputs for a given currency, used when nothing
 * is persisted yet and on reset.
 *
 * @param <I> the calculator's input bean type
 */
public interface CalculatorDefaults<I> {

    I forCurrency(SupportedCurrency currency);
}
