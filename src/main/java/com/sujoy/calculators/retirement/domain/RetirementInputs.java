package com.sujoy.calculators.retirement.domain;

import java.math.BigDecimal;

/**
 * Typed retirement-calculator input record. Percentages are stored as
 * percentages (e.g. {@code 12} for 12%), not decimal fractions.
 */
public record RetirementInputs(
        int currentAge,
        int retireAge,
        int lifeExp,
        BigDecimal corpus,
        BigDecimal monthlyExpenses,
        BigDecimal inflationPct,
        BigDecimal growthPrePct,
        BigDecimal growthPostPct,
        BigDecimal monthlyInvPre,
        BigDecimal sipGrowthPrePct,
        BigDecimal monthlyInvPost,
        BigDecimal sipGrowthPostPct
) {}
