package io.binarycodes.calculators.irr.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The outcome of an XIRR calculation.
 *
 * @param xirr         the headline annualised rate as a fraction (e.g.
 *                     {@code 0.12} for 12%); when {@link #status()} is
 *                     {@link XirrStatus#NON_UNIQUE} this is one of several roots
 * @param roots        every rate at which NPV = 0, ascending (fractions)
 * @param status       whether the rate is uniquely determined
 * @param signChanges  number of sign changes in the date-ordered cashflows;
 *                     more than one is what makes XIRR non-unique
 * @param totalInvested sum of money paid in (positive magnitude)
 * @param totalWithdrawn sum of money received (positive magnitude)
 * @param netCashflow  {@code totalWithdrawn − totalInvested}
 * @param paybackDate  first date the running (undiscounted) total turns
 *                     non-negative, if it ever does
 * @param cashflows    the expanded, date-sorted schedule with running totals
 * @param npvCurve     samples of NPV across a range of rates, for the chart
 */
public record XirrResult(
        BigDecimal xirr,
        List<BigDecimal> roots,
        XirrStatus status,
        int signChanges,
        BigDecimal totalInvested,
        BigDecimal totalWithdrawn,
        BigDecimal netCashflow,
        Optional<LocalDate> paybackDate,
        List<CashflowRow> cashflows,
        List<NpvPoint> npvCurve) {
}
