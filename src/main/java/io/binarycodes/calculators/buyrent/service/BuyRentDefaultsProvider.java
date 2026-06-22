package io.binarycodes.calculators.buyrent.service;

import io.binarycodes.calculators.base.common.CalculatorDefaults;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.buyrent.domain.BuyRentInputs;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * Loads {@code buyrent-defaults.json} from classpath into typed
 * {@link BuyRentInputs} per currency.
 */
@Service
public class BuyRentDefaultsProvider implements CalculatorDefaults<BuyRentInputs> {

    private final Resource defaultsResource;
    private final Map<SupportedCurrency, BuyRentInputs> defaults = new EnumMap<>(SupportedCurrency.class);

    public BuyRentDefaultsProvider(@Value("classpath:buyrent-defaults.json") Resource defaultsResource) {
        this.defaultsResource = defaultsResource;
    }

    @PostConstruct
    void load() throws IOException {
        final ObjectMapper objectMapper = JsonMapper.builder().build();
        try (InputStream stream = this.defaultsResource.getInputStream()) {
            final JsonNode root = objectMapper.readTree(stream);
            for (final SupportedCurrency currency : SupportedCurrency.values()) {
                final JsonNode node = root.get(currency.name());
                if (node != null) {
                    this.defaults.put(currency, toInputs(node));
                }
            }
        }
    }

    @Override
    public BuyRentInputs forCurrency(SupportedCurrency currency) {
        final BuyRentInputs inputs = this.defaults.get(currency);
        if (inputs != null) {
            return copy(inputs);
        }
        final BuyRentInputs inrDefault = this.defaults.get(SupportedCurrency.INR);
        return inrDefault == null ? fallback() : copy(inrDefault);
    }

    private static BuyRentInputs toInputs(JsonNode node) {
        final var inputs = new BuyRentInputs();
        inputs.setHomePrice(bd(node, "homePrice"));
        inputs.setDownPaymentPct(bd(node, "downPaymentPct"));
        inputs.setLoanTermYears(intField(node, "loanTermYears"));
        inputs.setMortgageRatePct(bd(node, "mortgageRatePct"));
        inputs.setPropertyTaxRatePct(bd(node, "propertyTaxRatePct"));
        inputs.setMaintenancePct(bd(node, "maintenancePct"));
        inputs.setAppreciationPct(bd(node, "appreciationPct"));
        inputs.setBuyingCostPct(bd(node, "buyingCostPct"));
        inputs.setSellingCostPct(bd(node, "sellingCostPct"));
        inputs.setMonthlyRent(bd(node, "monthlyRent"));
        inputs.setRentIncreasePct(bd(node, "rentIncreasePct"));
        inputs.setInvestmentReturnPct(bd(node, "investmentReturnPct"));
        inputs.setInflationRatePct(bd(node, "inflationRatePct"));
        inputs.setAnalysisYears(intField(node, "analysisYears"));
        inputs.setPropertyCapitalGainsTaxPct(bd(node, "propertyCapitalGainsTaxPct"));
        inputs.setInvestmentGainsTaxPct(bd(node, "investmentGainsTaxPct"));
        return inputs;
    }

    private static BigDecimal bd(JsonNode node, String field) {
        final JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.asString());
    }

    private static Integer intField(JsonNode node, String field) {
        final JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return Integer.valueOf(value.asString());
    }

    private static BuyRentInputs copy(BuyRentInputs source) {
        return new BuyRentInputs(
                source.getHomePrice(),
                source.getDownPaymentPct(),
                source.getLoanTermYears(),
                source.getMortgageRatePct(),
                source.getPropertyTaxRatePct(),
                source.getMaintenancePct(),
                source.getAppreciationPct(),
                source.getBuyingCostPct(),
                source.getSellingCostPct(),
                source.getMonthlyRent(),
                source.getRentIncreasePct(),
                source.getInvestmentReturnPct(),
                source.getInflationRatePct(),
                source.getAnalysisYears(),
                source.getPropertyCapitalGainsTaxPct(),
                source.getInvestmentGainsTaxPct());
    }

    private static BuyRentInputs fallback() {
        final var inputs = new BuyRentInputs();
        inputs.setHomePrice(BigDecimal.valueOf(5_000_000));
        inputs.setDownPaymentPct(BigDecimal.valueOf(20));
        inputs.setLoanTermYears(20);
        inputs.setMortgageRatePct(BigDecimal.valueOf(8.5));
        inputs.setPropertyTaxRatePct(BigDecimal.valueOf(0.5));
        inputs.setMaintenancePct(BigDecimal.valueOf(1.5));
        inputs.setAppreciationPct(BigDecimal.valueOf(6));
        inputs.setBuyingCostPct(BigDecimal.valueOf(7));
        inputs.setSellingCostPct(BigDecimal.valueOf(2));
        inputs.setMonthlyRent(BigDecimal.valueOf(25_000));
        inputs.setRentIncreasePct(BigDecimal.valueOf(5));
        inputs.setInvestmentReturnPct(BigDecimal.valueOf(10));
        inputs.setInflationRatePct(BigDecimal.valueOf(6));
        inputs.setAnalysisYears(20);
        inputs.setPropertyCapitalGainsTaxPct(BigDecimal.valueOf(20));
        inputs.setInvestmentGainsTaxPct(BigDecimal.valueOf(12.5));
        return inputs;
    }
}
