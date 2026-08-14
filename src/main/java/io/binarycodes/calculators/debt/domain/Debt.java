package io.binarycodes.calculators.debt.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A single debt in the payoff plan. Rates are stored as percentages ({@code 19.9}
 * for 19.9%). The effective monthly minimum is
 * {@code max(minimumFloor, minimumPct% × balance)} capped at the outstanding
 * balance, where {@code minimumFloor} is {@link #minimumPayment} when set and a
 * currency-scaled default otherwise. Either {@link #minimumPayment} or
 * {@link #minimumPct} may be null; the floor still guarantees the debt
 * amortizes. {@link #promoAprPct} applies for the first {@link #promoMonths}
 * months, after which {@link #aprPct} takes over.
 *
 * <p>{@link #priority} marks a debt the user must keep current (e.g. the car
 * needed for work): its minimum is covered before any other debt's, so it is the
 * last to default when the budget is tight.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Debt {

    private String name;
    private BigDecimal balance;
    private BigDecimal aprPct;
    private BigDecimal minimumPayment;
    private BigDecimal minimumPct;
    private BigDecimal promoAprPct;
    private Integer promoMonths;
    private boolean priority;
}
