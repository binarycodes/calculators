package io.binarycodes.calculators.debt.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Mutable input bean for the debt-payoff planner. {@link #extraPerMonth} is
 * added on top of the summed effective minimums to form the monthly budget;
 * {@link #extraStepUpPct} grows that extra each year; {@link #windfalls} are
 * one-off lump payments in specific months; {@link #strategy} is the headline
 * strategy shown in the summary; {@link #inflationRatePct} is optional and drives
 * the today's-money interest totals.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DebtPlanInputs {

    private List<Debt> debts = new ArrayList<>();
    private BigDecimal extraPerMonth;
    private BigDecimal extraStepUpPct;
    private List<Windfall> windfalls = new ArrayList<>();
    private PayoffStrategy strategy;
    private BigDecimal inflationRatePct;
}
