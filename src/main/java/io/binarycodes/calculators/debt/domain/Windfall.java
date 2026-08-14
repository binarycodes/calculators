package io.binarycodes.calculators.debt.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A one-off lump payment made in a single month, on top of the recurring extra.
 * {@link #month} is 1-based from the plan start. The windfall joins that month's
 * surplus and funnels to the current target(s) in strategy order, cascading
 * within the month like any other surplus.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Windfall {

    private Integer month;
    private BigDecimal amount;
}
