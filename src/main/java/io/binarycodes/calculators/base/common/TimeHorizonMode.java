package io.binarycodes.calculators.base.common;

/**
 * How the user expresses a time horizon. Each mode resolves to a single
 * integer month count via {@link TimeHorizon#resolveTotalMonths}. Shared
 * across calculators that project over a horizon (goal planner, inflation).
 */
public enum TimeHorizonMode {
    YEARS,
    AGES,
    TARGET_YEAR
}
