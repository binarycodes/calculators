package io.binarycodes.calculators.retirement.domain;

import io.binarycodes.calculators.base.common.Frequency;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A recurring future expense (rent, school fees, club dues…). Starts in
 * {@code year} and continues until {@code stopYear} (or indefinitely if
 * {@code stopYear} is null). {@code amount} is the value in today's money
 * for one period of {@link #frequency}; the calculator grows it forward
 * each year at {@code inflationPct}, falling back to the overall inflation
 * rate when this field is left unset (medical, education, and food
 * inflation can all differ from general inflation, so per-item rates are
 * supported).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RecurringExpense {

    private Integer year;
    private Integer stopYear;
    private String description;
    private Frequency frequency;
    private BigDecimal amount;
    private BigDecimal inflationPct;
}
