package io.binarycodes.calculators.investment.domain;

import io.binarycodes.calculators.base.common.Frequency;
import io.binarycodes.calculators.base.common.TimeHorizonMode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Mutable input bean for the investment calculator. Percentages are stored as
 * percentages ({@code 12} for 12%).
 *
 * <p>The projection has two phases. During the <b>investment</b> phase the user
 * contributes {@link #amount} at {@link #frequency} cadence, ramped by an annual
 * {@link #stepUpPct}; its length comes from the shared horizon fields
 * ({@link #horizonMode} + the mode-specific fields). During the subsequent
 * <b>hold</b> phase ({@link #holdYears} + {@link #holdMonths}) no contributions
 * are made and the corpus simply keeps compounding.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentInputs {

    private BigDecimal amount;
    private Frequency frequency;
    private BigDecimal growthRatePct;
    private BigDecimal taxRatePct;
    private BigDecimal inflationRatePct;
    private BigDecimal stepUpPct;

    // Investment phase length — shared Years / Ages / Target-Year selector.
    private TimeHorizonMode horizonMode;
    private Integer investYears;
    private Integer investMonths;
    private Integer currentAge;
    private Integer goalAge;
    private Integer targetYear;
    private Integer targetMonth;

    // Hold phase length — a plain duration after contributions stop.
    private Integer holdYears;
    private Integer holdMonths;
}
