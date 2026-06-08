package io.binarycodes.calculators.goal.domain;

/**
 * How the user wants to express the goal's deadline. Each mode resolves to a
 * single integer "years to goal" inside the calculator.
 */
public enum TimeHorizonMode {
    YEARS,
    AGES,
    TARGET_YEAR
}
