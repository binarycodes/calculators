package io.binarycodes.calculators.irr.service;

import io.binarycodes.calculators.irr.domain.Cashflow;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * The XIRR maths: net present value of dated cashflows and the rate(s) that
 * drive it to zero. Time is measured in years from the first cashflow on an
 * Actual/365 day-count, matching spreadsheet {@code XIRR}.
 *
 * <p>Roots are found by scanning the NPV curve for sign changes and bisecting
 * each bracket. Bisection (rather than Newton-Raphson) is deliberate: it cannot
 * diverge and it surfaces <em>every</em> root, which is exactly what a
 * non-conventional schedule with multiple IRRs needs. All callers receive rates
 * as fractions (e.g. {@code 0.12} for 12%).</p>
 */
public final class Xirr {

    /** A rate must exceed −100%; discounting at −100% divides by zero. */
    private static final double MIN_RATE = -0.999_999;
    private static final double SCAN_HIGH = 100.0;
    private static final double SCAN_STEP = 0.0025;
    private static final int BISECTION_STEPS = 200;
    private static final double DUPLICATE_ROOT_GAP = 1e-4;
    private static final double DAYS_PER_YEAR = 365.0;

    private Xirr() {
    }

    /**
     * Net present value of {@code cashflows} discounted at {@code rate} (a
     * fraction). The cashflows need not be sorted; the earliest date is the
     * reference point at which time is zero.
     */
    public static double npv(List<Cashflow> cashflows, double rate) {
        if (cashflows.isEmpty()) {
            return 0.0;
        }
        final java.time.LocalDate base = earliestDate(cashflows);
        double sum = 0.0;
        for (final Cashflow cashflow : cashflows) {
            final double years = ChronoUnit.DAYS.between(base, cashflow.date()) / DAYS_PER_YEAR;
            sum += cashflow.amount().doubleValue() / Math.pow(1.0 + rate, years);
        }
        return sum;
    }

    /**
     * Every rate (fraction, ascending) at which {@link #npv} crosses zero over
     * the scanned range. Empty when the cashflows never change sign or no root
     * lies in range.
     */
    public static List<BigDecimal> roots(List<Cashflow> cashflows) {
        final List<BigDecimal> found = new ArrayList<>();
        if (signChanges(cashflows) == 0) {
            return found;
        }
        double previousRate = MIN_RATE;
        double previousNpv = npv(cashflows, previousRate);
        for (double rate = MIN_RATE + SCAN_STEP; rate <= SCAN_HIGH; rate += SCAN_STEP) {
            final double currentNpv = npv(cashflows, rate);
            if (previousNpv == 0.0) {
                addRoot(found, previousRate);
            } else if (previousNpv * currentNpv < 0.0) {
                addRoot(found, bisect(cashflows, previousRate, currentNpv > 0.0 ? rate : previousRate,
                        currentNpv > 0.0 ? previousRate : rate));
            }
            previousRate = rate;
            previousNpv = currentNpv;
        }
        return found;
    }

    /**
     * Sign changes in the date-ordered cashflow amounts, ignoring zeros. A
     * conventional investment (outflows then inflows) has exactly one; more than
     * one means the IRR may not be unique (Descartes' rule of signs).
     */
    public static int signChanges(List<Cashflow> cashflows) {
        final List<Cashflow> ordered = new ArrayList<>(cashflows);
        ordered.sort((left, right) -> left.date().compareTo(right.date()));
        int changes = 0;
        int previousSign = 0;
        for (final Cashflow cashflow : ordered) {
            final int sign = cashflow.amount().signum();
            if (sign == 0) {
                continue;
            }
            if (previousSign != 0 && sign != previousSign) {
                changes++;
            }
            previousSign = sign;
        }
        return changes;
    }

    private static void addRoot(List<BigDecimal> found, double rate) {
        for (final BigDecimal existing : found) {
            if (Math.abs(existing.doubleValue() - rate) < DUPLICATE_ROOT_GAP) {
                return;
            }
        }
        found.add(BigDecimal.valueOf(rate));
    }

    /**
     * Bisect for the root between {@code negativeNpvRate} (where NPV &lt; 0) and
     * {@code positiveNpvRate} (where NPV &gt; 0). The bracket endpoints are
     * passed pre-oriented so the loop stays branch-free.
     */
    private static double bisect(List<Cashflow> cashflows, double low, double positiveNpvRate, double negativeNpvRate) {
        double positiveRate = positiveNpvRate;
        double negativeRate = negativeNpvRate;
        double midpoint = low;
        for (int step = 0; step < BISECTION_STEPS; step++) {
            midpoint = (positiveRate + negativeRate) / 2.0;
            final double value = npv(cashflows, midpoint);
            if (value > 0.0) {
                positiveRate = midpoint;
            } else {
                negativeRate = midpoint;
            }
        }
        return midpoint;
    }

    private static java.time.LocalDate earliestDate(List<Cashflow> cashflows) {
        java.time.LocalDate earliest = cashflows.get(0).date();
        for (final Cashflow cashflow : cashflows) {
            if (cashflow.date().isBefore(earliest)) {
                earliest = cashflow.date();
            }
        }
        return earliest;
    }
}
