package io.binarycodes.calculators.debt.domain;

import java.math.BigDecimal;

/**
 * The full debt-payoff analysis: the chosen strategy's {@link #primary} schedule,
 * the two canonical strategies (kept as references for the chart and the "vs"
 * card even when the primary is {@link PayoffStrategy#CUSTOM}), and the
 * minimums-only baseline. {@link #interestSaved} and {@link #monthsSaved} are the
 * primary strategy's advantage over paying minimums only.
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
        return switch (primaryStrategy) {
            case AVALANCHE -> snowball;
            case SNOWBALL, CUSTOM -> avalanche;
        };
    }

    /** The strategy whose name the "vs" card shows. */
    public PayoffStrategy alternativeStrategy() {
        return switch (primaryStrategy) {
            case AVALANCHE -> PayoffStrategy.SNOWBALL;
            case SNOWBALL, CUSTOM -> PayoffStrategy.AVALANCHE;
        };
    }
}
