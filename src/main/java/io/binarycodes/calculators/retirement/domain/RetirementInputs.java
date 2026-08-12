package io.binarycodes.calculators.retirement.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Mutable input bean for the retirement calculator. Percentages are stored as
 * percentages ({@code 12} for 12%), not decimal fractions. Fields are nullable
 * so a freshly-constructed instance has no values until populated.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RetirementInputs {

    private Integer currentAge;
    private Integer retireAge;
    private Integer lifeExp;

    private BigDecimal corpus;
    private BigDecimal monthlyExpenses;

    private BigDecimal inflationPct;
    private BigDecimal growthPrePct;
    private BigDecimal growthPostPct;
    private BigDecimal corpusTaxRatePct;

    // Investment contributions, each an independent accumulating stream.
    // Pre-retirement streams run through the working years; post-retirement
    // streams run through retirement.
    private List<Contribution> preRetirementContributions = new ArrayList<>();
    private List<Contribution> postRetirementContributions = new ArrayList<>();

    private List<FutureExpense> futureExpenses = new ArrayList<>();
    private List<RetirementBenefit> retirementBenefits = new ArrayList<>();
    private List<FutureIncome> futureIncomes = new ArrayList<>();
    private List<RecurringExpense> recurringExpenses = new ArrayList<>();
    private List<RecurringIncome> recurringIncomes = new ArrayList<>();
}
