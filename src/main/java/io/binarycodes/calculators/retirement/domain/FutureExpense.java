package io.binarycodes.calculators.retirement.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A one-off planned expense (e.g. car purchase, wedding, knee replacement)
 * that should fold into the corpus drawdown in the year it occurs.
 * {@code amount} is expressed in today's money; {@code inflationPct} is the
 * per-item inflation rate used to project it forward to {@code year}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FutureExpense {

    private Integer year;
    private String description;
    private BigDecimal amount;
    private BigDecimal inflationPct;
}
