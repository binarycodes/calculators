package io.binarycodes.calculators.irr.domain;

import io.binarycodes.calculators.base.common.Frequency;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A repeating cashflow: {@code count} payments of {@code amount}, the first on
 * {@code startDate} and each subsequent one {@link Frequency} later. The
 * {@code amount} is a positive magnitude; the owning section decides its sign.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RecurringCashflow {
    private LocalDate startDate;
    private Frequency frequency;
    private Integer count;
    private String description;
    private BigDecimal amount;
}
