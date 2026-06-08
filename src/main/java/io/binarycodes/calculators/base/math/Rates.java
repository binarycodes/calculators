package io.binarycodes.calculators.base.math;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Shared rate-math helpers used across calculators. Arithmetic runs under
 * {@link MathContext#DECIMAL64} so results align with the rest of the codebase
 * and avoid rounding drift on equality assertions.
 */
public final class Rates {

    public static final MathContext CONTEXT = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Rates() {
    }

    /**
     * Convert a percentage like {@code 12} to its decimal fraction {@code 0.12}.
     * A {@code null} percentage is treated as zero so callers can pass optional
     * fields without a null check.
     */
    public static BigDecimal pctToFraction(BigDecimal percentage) {
        return percentage == null ? BigDecimal.ZERO : percentage.divide(HUNDRED, CONTEXT);
    }

    /**
     * Convert an annual rate to its monthly equivalent: {@code (1+annual)^(1/12) − 1}.
     * Uses {@link Math#pow} for the fractional exponent (double precision, ~15
     * significant digits) and returns the result in {@link BigDecimal} so it
     * composes with the rest of the calculator's arithmetic.
     */
    public static BigDecimal monthlyFromAnnual(BigDecimal annualRate) {
        if (annualRate == null || annualRate.signum() == 0) {
            return BigDecimal.ZERO;
        }
        final double monthly = Math.pow(1.0 + annualRate.doubleValue(), 1.0 / 12.0) - 1.0;
        return BigDecimal.valueOf(monthly);
    }

    /**
     * {@code (1 + rate)^exponent}. Iterative multiplication keeps the result in
     * {@link BigDecimal} space; for the small exponents this codebase uses
     * (years in a human lifetime), this is fast enough and stays exact under
     * {@link #CONTEXT}.
     */
    public static BigDecimal pow1plus(BigDecimal rate, int exponent) {
        if (exponent == 0) {
            return BigDecimal.ONE;
        }
        final BigDecimal base = BigDecimal.ONE.add(rate, CONTEXT);
        BigDecimal accumulator = BigDecimal.ONE;
        for (int step = 0; step < exponent; step++) {
            accumulator = accumulator.multiply(base, CONTEXT);
        }
        return accumulator;
    }
}
