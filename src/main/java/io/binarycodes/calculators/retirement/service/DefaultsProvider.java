package io.binarycodes.calculators.retirement.service;

import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;
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
 * Loads {@code defaults.json} from classpath into typed
 * {@link RetirementInputs} per currency. JSON shape mirrors the legacy file
 * — string-encoded numbers, keyed by currency code.
 */
@Service
public class DefaultsProvider {

    private final Resource defaultsResource;
    private final Map<SupportedCurrency, RetirementInputs> defaults = new EnumMap<>(SupportedCurrency.class);

    public DefaultsProvider(@Value("classpath:defaults.json") Resource defaultsResource) {
        this.defaultsResource = defaultsResource;
    }

    @PostConstruct
    void load() throws IOException {
        final ObjectMapper om = JsonMapper.builder().build();
        try (InputStream in = this.defaultsResource.getInputStream()) {
            final JsonNode root = om.readTree(in);
            for (final SupportedCurrency c : SupportedCurrency.values()) {
                final JsonNode node = root.get(c.name());
                if (node == null) {
                    continue;
                }
                this.defaults.put(c, toInputs(node));
            }
        }
    }

    public RetirementInputs forCurrency(SupportedCurrency c) {
        final RetirementInputs in = this.defaults.get(c);
        if (in != null) {
            return in;
        }
        // Fall back to INR if a currency entry is missing.
        return this.defaults.getOrDefault(SupportedCurrency.INR, fallback());
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
        final JsonNode v = n.get(field);
        if (v == null || v.isNull()) {
            return BigDecimal.ZERO;
        }
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
