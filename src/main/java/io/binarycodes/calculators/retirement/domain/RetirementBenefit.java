package io.binarycodes.calculators.retirement.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A retirement-period inflow (pension, gratuity, social security lump sum,
 * annuity payout) received on the retirement-age year. {@code taxRatePct} is
 * applied immediately on receipt — the net amount
 * ({@code amount × (1 − taxRate)}) is what reaches the corpus.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RetirementBenefit {

    private String description;
    private BigDecimal amount;
    private BigDecimal taxRatePct;
}
