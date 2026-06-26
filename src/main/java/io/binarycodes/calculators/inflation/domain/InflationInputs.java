package io.binarycodes.calculators.inflation.domain;

import io.binarycodes.calculators.base.common.TimeHorizonMode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Mutable input bean for the inflation projection. The horizon fields mirror
 * the goal planner. Percentages are stored as percentages ({@code 6} for 6%).
 *
 * <p>{@link #amountIsToday} flips the direction of the projection: when
 * {@code true} the entered {@link #amount} is in today's money and the result
 * is its inflated value at the horizon; when {@code false} the amount is a
 * future value and the result is its purchasing power in today's money.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InflationInputs {

    private BigDecimal amount;
    private BigDecimal inflationRatePct;
    /**
     * Uncertainty band around {@link #inflationRatePct}, in percentage points
     * ({@code 2} means ±2%). Drives the area-range chart; {@code null}/zero
     * collapses the band onto the central line. Does not affect the headline
     * forward/backward result, which uses the central rate.
     */
    private BigDecimal inflationVariationPct;
    private boolean amountIsToday;

    private TimeHorizonMode horizonMode;
    private Integer yearsToGoal;
    private Integer monthsToGoal;
    private Integer currentAge;
    private Integer goalAge;
    private Integer targetYear;
    private Integer targetMonth;
}
