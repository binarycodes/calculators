package io.binarycodes.calculators.goal.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Mutable input bean for the goal planner. Percentages are stored as
 * percentages ({@code 12} for 12%), not decimal fractions. Fields are
 * nullable so a freshly-constructed instance has no values until populated
 * by the binder or defaults provider.
 *
 * <p>Each entry in {@link #investments} represents one bucket with its own
 * corpus, growth, tax, and allocation share of every monthly SIP. The
 * allocations are expected to sum to 100% (enforced at the form layer).</p>
 *
 * <p>Only one of {@link #yearsToGoal}+{@link #monthsToGoal}, the
 * {@link #currentAge}/{@link #goalAge} pair, or
 * {@link #targetYear}+{@link #targetMonth} is consulted at calculation time,
 * picked by {@link #horizonMode}.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GoalInputs {

    private BigDecimal goalAmount;
    private BigDecimal inflationRatePct;

    private List<Investment> investments = new ArrayList<>();

    private TimeHorizonMode horizonMode;
    private Integer yearsToGoal;
    private Integer monthsToGoal;
    private Integer currentAge;
    private Integer goalAge;
    private Integer targetYear;
    private Integer targetMonth;
}
