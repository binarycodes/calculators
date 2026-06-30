package io.binarycodes.calculators.irr.service;

import io.binarycodes.calculators.base.math.Rates;
import io.binarycodes.calculators.irr.domain.Cashflow;
import io.binarycodes.calculators.irr.domain.CashflowRow;
import io.binarycodes.calculators.irr.domain.DatedCashflow;
import io.binarycodes.calculators.irr.domain.NpvPoint;
import io.binarycodes.calculators.irr.domain.RecurringCashflow;
import io.binarycodes.calculators.irr.domain.XirrInputs;
import io.binarycodes.calculators.irr.domain.XirrResult;
import io.binarycodes.calculators.irr.domain.XirrStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Turns {@link XirrInputs} into an {@link XirrResult}: it expands the one-off
 * and recurring entries into a signed, date-ordered schedule (investments
 * negative, withdrawals positive), solves for the IRR(s) with {@link Xirr}, and
 * derives the totals, payback point, and NPV-vs-rate curve the UI renders.
 *
 * <p>Stateless static utility, mirroring the other calculators. Invalid input
 * (too few cashflows, or one-sided cashflows that can never break even) is
 * reported by throwing {@link IllegalArgumentException} with a translation key,
 * which the view surfaces as a form-level message. A schedule that <em>does</em>
 * resolve but to several rates is <em>not</em> an error: it comes back with
 * {@link XirrStatus#NON_UNIQUE} so the view can warn while still showing every
 * root.</p>
 */
public final class XirrCalculator {

    private static final int INVESTMENT_SIGN = -1;
    private static final int WITHDRAWAL_SIGN = 1;

    private static final double CURVE_LOW_RATE = -0.9;
    private static final double CURVE_MIN_HIGH_RATE = 1.0;
    private static final double CURVE_MIN_RATE = -0.9999; // just shy of the -100% asymptote
    private static final double CURVE_ROOT_HEADROOM = 0.2;
    private static final int CURVE_SAMPLES = 120;

    private XirrCalculator() {
    }

    public static XirrResult calculate(XirrInputs inputs) {
        final List<Cashflow> cashflows = expand(inputs);
        if (cashflows.size() < 2) {
            throw new IllegalArgumentException("irr.validation.needTwoCashflows");
        }
        if (Xirr.signChanges(cashflows) == 0) {
            throw new IllegalArgumentException("irr.validation.needBothDirections");
        }

        cashflows.sort(Comparator.comparing(Cashflow::date));

        final List<BigDecimal> roots = Xirr.roots(cashflows);
        if (roots.isEmpty()) {
            throw new IllegalArgumentException("irr.validation.noRate");
        }

        final XirrStatus status = roots.size() > 1 ? XirrStatus.NON_UNIQUE : XirrStatus.UNIQUE;
        final BigDecimal headline = headlineRoot(roots);

        final BigDecimal totalInvested = sumMagnitude(cashflows, INVESTMENT_SIGN);
        final BigDecimal totalWithdrawn = sumMagnitude(cashflows, WITHDRAWAL_SIGN);

        final List<CashflowRow> rows = withRunningTotals(cashflows);
        final Optional<LocalDate> paybackDate = paybackDate(rows);

        return new XirrResult(
                headline,
                roots,
                status,
                Xirr.signChanges(cashflows),
                totalInvested,
                totalWithdrawn,
                totalWithdrawn.subtract(totalInvested),
                paybackDate,
                rows,
                npvCurve(cashflows, roots));
    }

    private static List<Cashflow> expand(XirrInputs inputs) {
        final List<Cashflow> out = new ArrayList<>();
        addOneOff(out, inputs.getOneOffInvestments(), INVESTMENT_SIGN);
        addOneOff(out, inputs.getOneOffWithdrawals(), WITHDRAWAL_SIGN);
        addRecurring(out, inputs.getRecurringInvestments(), INVESTMENT_SIGN);
        addRecurring(out, inputs.getRecurringWithdrawals(), WITHDRAWAL_SIGN);
        return out;
    }

    private static void addOneOff(List<Cashflow> out, List<DatedCashflow> items, int sign) {
        if (items == null) {
            return;
        }
        for (final DatedCashflow item : items) {
            if (item.getDate() == null || item.getAmount() == null || item.getAmount().signum() == 0) {
                continue;
            }
            out.add(new Cashflow(item.getDate(), signed(item.getAmount(), sign), item.getDescription()));
        }
    }

    private static void addRecurring(List<Cashflow> out, List<RecurringCashflow> items, int sign) {
        if (items == null) {
            return;
        }
        for (final RecurringCashflow item : items) {
            if (item.getStartDate() == null || item.getAmount() == null || item.getAmount().signum() == 0
                    || item.getFrequency() == null || item.getCount() == null || item.getCount() < 1) {
                continue;
            }
            final BigDecimal amount = signed(item.getAmount(), sign);
            final int monthsPerPeriod = item.getFrequency().monthsPerPeriod();
            for (int occurrence = 0; occurrence < item.getCount(); occurrence++) {
                final LocalDate date = item.getStartDate().plusMonths((long) occurrence * monthsPerPeriod);
                out.add(new Cashflow(date, amount, item.getDescription()));
            }
        }
    }

    private static BigDecimal signed(BigDecimal magnitude, int sign) {
        final BigDecimal positive = magnitude.abs();
        return sign < 0 ? positive.negate() : positive;
    }

    /** The root closest to zero — the most plausible headline among several. */
    private static BigDecimal headlineRoot(List<BigDecimal> roots) {
        BigDecimal closest = roots.get(0);
        for (final BigDecimal root : roots) {
            if (root.abs().compareTo(closest.abs()) < 0) {
                closest = root;
            }
        }
        return closest;
    }

    private static BigDecimal sumMagnitude(List<Cashflow> cashflows, int sign) {
        BigDecimal sum = BigDecimal.ZERO;
        for (final Cashflow cashflow : cashflows) {
            if (cashflow.amount().signum() == sign) {
                sum = sum.add(cashflow.amount().abs(), Rates.CONTEXT);
            }
        }
        return sum;
    }

    private static List<CashflowRow> withRunningTotals(List<Cashflow> cashflows) {
        final List<CashflowRow> rows = new ArrayList<>(cashflows.size());
        BigDecimal cumulative = BigDecimal.ZERO;
        for (final Cashflow cashflow : cashflows) {
            cumulative = cumulative.add(cashflow.amount(), Rates.CONTEXT);
            rows.add(new CashflowRow(cashflow.date(), cashflow.description(), cashflow.amount(), cumulative));
        }
        return rows;
    }

    /** First date the running total turns non-negative — money is back in hand. */
    private static Optional<LocalDate> paybackDate(List<CashflowRow> rows) {
        for (final CashflowRow row : rows) {
            if (row.cumulative().signum() >= 0) {
                return Optional.of(row.date());
            }
        }
        return Optional.empty();
    }

    private static List<NpvPoint> npvCurve(List<Cashflow> cashflows, List<BigDecimal> roots) {
        final double lowRate = curveLowRate(roots);
        final double highRate = curveHighRate(roots);
        final double step = (highRate - lowRate) / CURVE_SAMPLES;
        final List<NpvPoint> curve = new ArrayList<>(CURVE_SAMPLES + 1);
        for (int sample = 0; sample <= CURVE_SAMPLES; sample++) {
            final double rate = lowRate + sample * step;
            curve.add(new NpvPoint(BigDecimal.valueOf(rate), BigDecimal.valueOf(Xirr.npv(cashflows, rate))));
        }
        return curve;
    }

    // The curve must span every root the banner marks, so each plotted root line
    // lands on a real zero-crossing. Start from the default window and stretch
    // either end (with headroom) to enclose the outermost roots; the low end is
    // floored just above the -100% asymptote where NPV blows up.
    private static double curveLowRate(List<BigDecimal> roots) {
        double low = CURVE_LOW_RATE;
        for (final BigDecimal root : roots) {
            low = Math.min(low, root.doubleValue() - CURVE_ROOT_HEADROOM);
        }
        return Math.max(low, CURVE_MIN_RATE);
    }

    private static double curveHighRate(List<BigDecimal> roots) {
        double high = CURVE_MIN_HIGH_RATE;
        for (final BigDecimal root : roots) {
            high = Math.max(high, root.doubleValue() + CURVE_ROOT_HEADROOM);
        }
        return high;
    }
}
