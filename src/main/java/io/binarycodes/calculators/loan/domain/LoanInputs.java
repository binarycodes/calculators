package io.binarycodes.calculators.loan.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Mutable input bean for the loan / EMI calculator. Percentages are stored as
 * percentages ({@code 8.5} for 8.5%).
 *
 * <p>The first block is the loan itself ({@link #loanAmount}, {@link #annualRatePct},
 * tenure as {@link #tenureYears} + {@link #tenureMonths}), plus an
 * {@link #inflationRatePct} used only to express the cost in today's money.</p>
 *
 * <p>The remaining fields are optional prepayment levers; all default to zero so
 * the calculator behaves like a plain EMI calculator until they are set:
 * <ul>
 *   <li>{@link #extraPerPeriod} paid every {@link #extraFrequency} on top of the EMI;</li>
 *   <li>{@link #extraEmisPerYear} additional full EMIs paid once a year;</li>
 *   <li>{@link #emiStepUpPct} annual increase of the EMI itself (a "pay more"
 *       lever, so it only shortens the tenure).</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoanInputs {

    private BigDecimal loanAmount;
    private BigDecimal annualRatePct;
    private Integer tenureYears;
    private Integer tenureMonths;
    private BigDecimal inflationRatePct;

    // Prepayment levers (optional; zero = none).
    private BigDecimal extraPerPeriod;
    private PrepaymentFrequency extraFrequency;
    private Integer extraEmisPerYear;
    private BigDecimal emiStepUpPct;
}
