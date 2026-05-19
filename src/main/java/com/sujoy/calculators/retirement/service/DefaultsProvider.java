package com.sujoy.calculators.retirement.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.sujoy.calculators.base.money.Currency;
import com.sujoy.calculators.retirement.domain.RetirementInputs;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * Loads {@code defaults.json} from classpath into typed
 * {@link RetirementInputs} per currency. JSON shape mirrors the legacy file
 * — string-encoded numbers, keyed by currency code.
 */
@Service
public class DefaultsProvider {

    private final Resource defaultsResource;
    private final Map<Currency, RetirementInputs> defaults = new EnumMap<>(Currency.class);

    public DefaultsProvider(@Value("classpath:defaults.json") Resource defaultsResource) {
        this.defaultsResource = defaultsResource;
    }

    @PostConstruct
    void load() throws IOException {
        ObjectMapper om = JsonMapper.builder().build();
        try (InputStream in = defaultsResource.getInputStream()) {
            JsonNode root = om.readTree(in);
            for (Currency c : Currency.values()) {
                JsonNode node = root.get(c.name());
                if (node == null) continue;
                defaults.put(c, toInputs(node));
            }
        }
    }

    public RetirementInputs forCurrency(Currency c) {
        RetirementInputs in = defaults.get(c);
        if (in != null) return in;
        // Fall back to INR if a currency entry is missing.
        return defaults.getOrDefault(Currency.INR, fallback());
    }

    private RetirementInputs toInputs(JsonNode n) {
        return new RetirementInputs(
                n.get("currentAge").asInt(),
                n.get("retireAge").asInt(),
                n.get("lifeExp").asInt(),
                bd(n, "corpus"),
                bd(n, "monthlyExp"),
                bd(n, "inflation"),
                bd(n, "growthPre"),
                bd(n, "growthPost"),
                bd(n, "monthlyInvPre"),
                bd(n, "sipGrowthPre"),
                bd(n, "monthlyInvPost"),
                bd(n, "sipGrowthPost"));
    }

    private static BigDecimal bd(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) return BigDecimal.ZERO;
        return new BigDecimal(v.asText());
    }

    private static RetirementInputs fallback() {
        return new RetirementInputs(35, 60, 90,
                BigDecimal.valueOf(5_000_000), BigDecimal.valueOf(50_000),
                BigDecimal.valueOf(6),
                BigDecimal.valueOf(12), BigDecimal.valueOf(8),
                BigDecimal.valueOf(25_000), BigDecimal.valueOf(12),
                BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
