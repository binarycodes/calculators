package io.binarycodes.calculators.irr.domain;

/**
 * The determinacy of an XIRR calculation.
 *
 * <ul>
 *   <li>{@link #UNIQUE} — exactly one rate solves NPV = 0; the result is well
 *       defined.</li>
 *   <li>{@link #NON_UNIQUE} — the cashflows change sign more than once, so NPV
 *       crosses zero at several rates; no single XIRR is meaningful. The headline
 *       rate is reported with a warning and every root is listed.</li>
 * </ul>
 */
public enum XirrStatus {
    UNIQUE,
    NON_UNIQUE
}
