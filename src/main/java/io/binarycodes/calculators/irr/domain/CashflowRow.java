package io.binarycodes.calculators.irr.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of the cashflow schedule grid: a dated signed {@code amount} and the
 * running {@code cumulative} (undiscounted) total up to and including that date.
 *
 * @param date        the cashflow date
 * @param description optional label (nullable)
 * @param amount      signed amount — negative outflow, positive inflow
 * @param cumulative  running sum of all amounts up to this row
 */
public record CashflowRow(LocalDate date, String description, BigDecimal amount, BigDecimal cumulative) {
}
