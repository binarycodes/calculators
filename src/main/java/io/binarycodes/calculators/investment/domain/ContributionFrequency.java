package io.binarycodes.calculators.investment.domain;

/**
 * Cadence of an investment contribution — monthly or yearly. Kept local to the
 * investment feature so it stays decoupled from the retirement calculator's own
 * frequency type.
 */
public enum ContributionFrequency {
    MONTHLY,
    YEARLY
}
