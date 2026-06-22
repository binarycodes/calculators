package io.binarycodes.calculators.buyrent.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Mutable input bean for the buy-vs-rent calculator. Percentages are stored as
 * percentages ({@code 8.5} for 8.5%).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BuyRentInputs {

    private BigDecimal homePrice;
    private BigDecimal downPaymentPct;
    private Integer loanTermYears;
    private BigDecimal mortgageRatePct;

    private BigDecimal propertyTaxRatePct;
    private BigDecimal maintenancePct;
    private BigDecimal appreciationPct;
    private BigDecimal buyingCostPct;
    private BigDecimal sellingCostPct;

    private BigDecimal monthlyRent;
    private BigDecimal rentIncreasePct;

    private BigDecimal investmentReturnPct;
    private BigDecimal inflationRatePct;
    private Integer analysisYears;

    private BigDecimal propertyCapitalGainsTaxPct;
    private BigDecimal investmentGainsTaxPct;
}
