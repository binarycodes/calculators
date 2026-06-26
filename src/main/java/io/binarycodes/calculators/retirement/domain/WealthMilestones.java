package io.binarycodes.calculators.retirement.domain;

import io.binarycodes.calculators.base.money.SupportedCurrency;

import java.math.BigDecimal;
import java.util.List;

/**
 * Nominal-corpus wealth milestones, in ascending order, that earn a marker the
 * first year the corpus crosses them. The thresholds differ by currency: the
 * Indian set steps through lakh/crore figures meaningful locally (₹10L … ₹50cr),
 * while the Western set runs $1M … $1B. Lives in the retirement domain so the
 * money package stays unaware of this feature.
 */
public final class WealthMilestones {

    private static final List<BigDecimal> INR_THRESHOLDS = thresholds(
            1_000_000L, 5_000_000L, 10_000_000L, 50_000_000L, 100_000_000L, 500_000_000L);

    private static final List<BigDecimal> WESTERN_THRESHOLDS = thresholds(
            1_000_000L, 10_000_000L, 50_000_000L, 100_000_000L, 500_000_000L, 1_000_000_000L);

    private WealthMilestones() {
    }

    public static List<BigDecimal> thresholdsFor(SupportedCurrency currency) {
        return currency == SupportedCurrency.INR ? INR_THRESHOLDS : WESTERN_THRESHOLDS;
    }

    private static List<BigDecimal> thresholds(long... values) {
        return java.util.Arrays.stream(values).mapToObj(BigDecimal::valueOf).toList();
    }
}
