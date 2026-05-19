package com.sujoy.calculators.base.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyFormatterTest {

    @Test
    void inr_uses_indian_grouping() {
        assertEquals("₹1,50,00,000", MoneyFormatter.format(BigDecimal.valueOf(15_000_000), Currency.INR));
        assertEquals("₹50,000",      MoneyFormatter.format(BigDecimal.valueOf(50_000),     Currency.INR));
    }

    @Test
    void usd_uses_western_grouping() {
        assertEquals("$75,000",      MoneyFormatter.format(BigDecimal.valueOf(75_000),     Currency.USD));
        assertEquals("$1,234,567",   MoneyFormatter.format(BigDecimal.valueOf(1_234_567),  Currency.USD));
    }

    @Test
    void eur_uses_de_grouping() {
        assertEquals("€60.000",      MoneyFormatter.format(BigDecimal.valueOf(60_000),     Currency.EUR));
    }

    @Test
    void negative_money_uses_minus_sign() {
        assertEquals("−₹7,53,161",   MoneyFormatter.format(BigDecimal.valueOf(-753_161),   Currency.INR));
    }

    @Test
    void short_format_indian() {
        // Mirrors the JS toFixed(1) behaviour: "1.0 Cr" for exactly 1e7.
        assertEquals("₹1.0 Cr", MoneyFormatter.formatShort(BigDecimal.valueOf(10_000_000),  Currency.INR));
        assertEquals("₹1.5 Cr", MoneyFormatter.formatShort(BigDecimal.valueOf(15_000_000),  Currency.INR));
        assertEquals("₹1.0 L",  MoneyFormatter.formatShort(BigDecimal.valueOf(100_000),     Currency.INR));
    }

    @Test
    void short_format_western() {
        assertEquals("$1.2M",   MoneyFormatter.formatShort(BigDecimal.valueOf(1_234_567),  Currency.USD));
        assertEquals("$60.0k",  MoneyFormatter.formatShort(BigDecimal.valueOf(60_000),     Currency.USD));
    }
}
