package io.binarycodes.calculators.debt.domain;

import java.math.BigDecimal;

/**
 * The full debt-payoff analysis: the chosen strategy's {@link #primary} schedule
 * (the same object as {@link #avalanche} or {@link #snowball}) and the
 * minimums-only {@link #baseline}. {@link #interestSaved} and
 * {@link #monthsSaved} are the primary strategy's advantage over paying minimums
 * only.
 */
public record DebtPlanResult(
        DebtScheduleResult primary,
        DebtScheduleResult avalanche,
        DebtScheduleResult snowball,
        DebtScheduleResult baseline,
        PayoffStrategy primaryStrategy,
        BigDecimal interestSaved,
        int monthsSaved,
        BigDecimal realInterestSaved
) {

    /**
     * The strategy the primary schedule is measured against in the "vs" card:
     * the other canonical strategy for avalanche/snowball, and avalanche (the
     * interest-optimal reference) for a custom order.
     */
    public DebtScheduleResult alternative() {
        return primaryStrategy == PayoffStrategy.SNOWBALL ? avalanche : snowball;
    }

    /** The strategy whose name the "vs" card shows. */
    public PayoffStrategy alternativeStrategy() {
        return primaryStrategy == PayoffStrategy.SNOWBALL ? PayoffStrategy.AVALANCHE : PayoffStrategy.SNOWBALL;
    }
}
