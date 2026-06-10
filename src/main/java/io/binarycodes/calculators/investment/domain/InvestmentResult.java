package io.binarycodes.calculators.investment.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Aggregate output of {@code InvestmentCalculator.calculate(...)}.
 *
 * @param investmentMonths  length of the contribution phase in months
 * @param holdMonths        length of the no-contribution hold phase in months
 * @param totalInvested     sum of all contributions (principal)
 * @param maturityValue     gross corpus at the end of the horizon, before tax
 * @param gains             {@code maturityValue − totalInvested}
 * @param taxAtExit         tax on the gains portion if fully withdrawn at the end
 * @param netValue          {@code maturityValue − taxAtExit}
 * @param buyingPowerToday  {@link #netValue} deflated to today's money at the inflation rate
 * @param rows              year-by-year projection
 */
public record InvestmentResult(
        int investmentMonths,
        int holdMonths,
        BigDecimal totalInvested,
        BigDecimal maturityValue,
        BigDecimal gains,
        BigDecimal taxAtExit,
        BigDecimal netValue,
        BigDecimal buyingPowerToday,
        List<InvestmentYear> rows
) {
    public int totalMonths() {
        return investmentMonths + holdMonths;
    }
}
