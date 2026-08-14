package io.binarycodes.calculators.debt.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Mutable input bean for the debt-payoff planner. {@link #monthlyBudget} is the
 * total the user can pay each month; the plan distributes it across the debts —
 * covering each debt's minimum first, then funnelling the remainder to the
 * strategy's target. {@link #budgetStepUpPct} grows that budget each year;
 * {@link #defaultFeePerMonth} is charged to a debt in any month the budget can't
 * cover its minimum; {@link #windfalls} are one-off lump payments in specific
 * months; {@link #strategy} is the headline strategy; {@link #inflationRatePct}
 * drives the today's-money interest totals.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DebtPlanInputs {

    private List<Debt> debts = new ArrayList<>();
    private BigDecimal monthlyBudget;
    private BigDecimal budgetStepUpPct;
    private BigDecimal defaultFeePerMonth;
    private List<Windfall> windfalls = new ArrayList<>();
    private PayoffStrategy strategy;
    private BigDecimal inflationRatePct;
}
