package io.binarycodes.calculators.retirement.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A recurring future income (rental income, side-gig, etc.). Starts in
 * {@code year} and continues indefinitely thereafter. {@code amount} is the
 * nominal value for one period of {@link #frequency} (no inflation applied;
 * the amount is treated as the actual cashflow received each period).
 * {@code taxRatePct} is applied immediately on receipt.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RecurringIncome {

    private Integer year;
    private Integer stopYear;
    private String description;
    private Frequency frequency;
    private BigDecimal amount;
    private BigDecimal taxRatePct;
}
