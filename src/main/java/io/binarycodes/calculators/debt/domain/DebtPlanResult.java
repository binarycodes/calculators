package io.binarycodes.calculators.debt.domain;

import java.math.BigDecimal;

/**
 * The full debt-payoff analysis: both strategies plus the minimums-only
 * baseline, with the savings of the {@link #primaryStrategy} measured against the
 * baseline. {@link #interestSaved} and {@link #monthsSaved} are the primary
 * strategy's advantage over paying minimums only.
 */
public record DebtPlanResult(
        DebtScheduleResult avalanche,
        DebtScheduleResult snowball,
        DebtScheduleResult baseline,
        PayoffStrategy primaryStrategy,
        BigDecimal interestSaved,
        int monthsSaved,
        BigDecimal realInterestSaved
) {

    /** The schedule for the headline strategy the user selected. */
    public DebtScheduleResult primary() {
        return primaryStrategy == PayoffStrategy.SNOWBALL ? snowball : avalanche;
    }

    /** The schedule for the strategy the user did not select. */
    public DebtScheduleResult alternative() {
        return primaryStrategy == PayoffStrategy.SNOWBALL ? avalanche : snowball;
    }
}
