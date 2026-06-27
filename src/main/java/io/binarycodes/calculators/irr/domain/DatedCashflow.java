package io.binarycodes.calculators.irr.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single one-off cashflow on a specific calendar date. The {@code amount} is
 * always entered as a positive magnitude; the section it belongs to (investment
 * or withdrawal) decides its sign when the schedule is expanded.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DatedCashflow {
    private LocalDate date;
    private String description;
    private BigDecimal amount;
}
