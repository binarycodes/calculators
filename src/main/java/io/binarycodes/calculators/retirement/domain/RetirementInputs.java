package io.binarycodes.calculators.retirement.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

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

    private BigDecimal monthlyInvPre;
    private BigDecimal sipGrowthPrePct;
    private BigDecimal monthlyInvPost;
    private BigDecimal sipGrowthPostPct;
}
