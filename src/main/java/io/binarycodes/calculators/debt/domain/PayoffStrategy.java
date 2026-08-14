package io.binarycodes.calculators.debt.domain;

/**
 * How the surplus (budget beyond the covered minimums) is aimed while paying
 * down several debts. {@link #AVALANCHE} attacks the debt with the highest
 * ongoing (post-promo) APR first; {@link #SNOWBALL} attacks the smallest
 * original balance first. Both rank the debts once up front and hold that order
 * for the whole run.
 */
public enum PayoffStrategy {
    AVALANCHE,
    SNOWBALL
}
