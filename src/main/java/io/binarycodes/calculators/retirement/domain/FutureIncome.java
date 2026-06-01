package io.binarycodes.calculators.retirement.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A one-off future inflow (house sale, business liquidation, inheritance,
 * windfall) expected in {@code year}. {@code amount} is the nominal value
 * at that year (no inflation projection). {@code taxRatePct} is applied
 * immediately on receipt — the net amount lands in the corpus at the
 * start of {@code year} and grows thereafter at the main corpus rate.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FutureIncome {

    private Integer year;
    private String description;
    private BigDecimal amount;
    private BigDecimal taxRatePct;
}
