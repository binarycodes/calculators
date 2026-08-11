package io.binarycodes.calculators.buyrent.service;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.WebStorage;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import io.binarycodes.calculators.base.common.InputsStore;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.buyrent.domain.BuyRentInputs;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Per-session, per-currency cache of buy-vs-rent inputs, mirrored to browser
 * localStorage under {@value #STORAGE_KEY}.
 */
@Component
@VaadinSessionScope
public class BuyRentInputsStore implements InputsStore<BuyRentInputs> {

    static final String STORAGE_KEY = "bvr_inputs";
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final Map<SupportedCurrency, BuyRentInputs> cache = new EnumMap<>(SupportedCurrency.class);

    @Override
    public void load(Consumer<Map<SupportedCurrency, BuyRentInputs>> onLoaded) {
        final UI ui = UI.getCurrent();
        if (ui == null) {
            onLoaded.accept(Map.copyOf(this.cache));
            return;
        }
        WebStorage.getItem(STORAGE_KEY, raw -> {
            // The browser is the source of truth. Anything cached from an earlier
            // request must not outlive it, or a replayed session cookie would hand
            // back inputs the client no longer holds.
            this.cache.clear();
            if (raw != null && !raw.isBlank()) {
                try {
                    final JsonNode root = this.objectMapper.readTree(raw);
                    for (final SupportedCurrency currency : SupportedCurrency.values()) {
                        final JsonNode node = root.get(currency.name());
                        if (node != null) {
                            this.cache.put(currency, fromJsonNode(node));
                        }
                    }
                } catch (final Exception ignored) { /* corrupt blob → fall back */ }
            }
            onLoaded.accept(Map.copyOf(this.cache));
        });
    }

    @Override
    public BuyRentInputs get(SupportedCurrency currency) {
        return this.cache.get(currency);
    }

    @Override
    public void save(SupportedCurrency currency, BuyRentInputs inputs) {
        this.cache.put(currency, inputs);
        persist();
    }

    private void persist() {
        final UI ui = UI.getCurrent();
        if (ui == null) {
            return;
        }
        final ObjectNode root = this.objectMapper.createObjectNode();
        for (final var entry : this.cache.entrySet()) {
            root.set(entry.getKey().name(), toJsonNode(entry.getValue()));
        }
        WebStorage.setItem(STORAGE_KEY, root.toString());
    }

    @Override
    public ObjectNode toJsonNode(BuyRentInputs inputs) {
        final ObjectNode node = this.objectMapper.createObjectNode();
        node.put("homePrice", plain(inputs.getHomePrice()));
        node.put("downPaymentPct", plain(inputs.getDownPaymentPct()));
        node.put("loanTermYears", intStr(inputs.getLoanTermYears()));
        node.put("mortgageRatePct", plain(inputs.getMortgageRatePct()));
        node.put("propertyTaxRatePct", plain(inputs.getPropertyTaxRatePct()));
        node.put("maintenancePct", plain(inputs.getMaintenancePct()));
        node.put("appreciationPct", plain(inputs.getAppreciationPct()));
        node.put("buyingCostPct", plain(inputs.getBuyingCostPct()));
        node.put("sellingCostPct", plain(inputs.getSellingCostPct()));
        node.put("monthlyRent", plain(inputs.getMonthlyRent()));
        node.put("rentIncreasePct", plain(inputs.getRentIncreasePct()));
        node.put("investmentReturnPct", plain(inputs.getInvestmentReturnPct()));
        node.put("inflationRatePct", plain(inputs.getInflationRatePct()));
        node.put("analysisYears", intStr(inputs.getAnalysisYears()));
        node.put("propertyCapitalGainsTaxPct", plain(inputs.getPropertyCapitalGainsTaxPct()));
        node.put("investmentGainsTaxPct", plain(inputs.getInvestmentGainsTaxPct()));
        return node;
    }

    @Override
    public BuyRentInputs fromJsonNode(JsonNode node) {
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

    private static Integer intField(JsonNode node, String field) {
        final JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asString().isBlank()) {
            return null;
        }
        return Integer.valueOf(value.asString());
    }

    private static BigDecimal bd(JsonNode node, String field) {
        final JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        final String text = value.asString();
        if (text == null || text.isBlank()) {
            return null;
        }
        return new BigDecimal(text);
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static String intStr(Integer value) {
        return value == null ? null : Integer.toString(value);
    }
}
