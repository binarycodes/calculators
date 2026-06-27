package io.binarycodes.calculators.irr.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A normalised, signed cashflow on a date: negative for money paid in
 * (investments / premiums), positive for money received (withdrawals /
 * returns). This is what the {@link io.binarycodes.calculators.irr.service.Xirr}
 * solver consumes after the form's recurring entries have been expanded.
 *
 * @param date        the calendar date the money moves
 * @param amount      signed amount — negative outflow, positive inflow
 * @param description optional label carried through for display (nullable)
 */
public record Cashflow(LocalDate date, BigDecimal amount, String description) {
}
