package io.binarycodes.calculators.base.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyFormatterTest {

    @Test
    void inr_uses_indian_grouping() {
        assertEquals("₹1,50,00,000", MoneyFormatter.format(BigDecimal.valueOf(15_000_000), SupportedCurrency.INR));
        assertEquals("₹50,000", MoneyFormatter.format(BigDecimal.valueOf(50_000), SupportedCurrency.INR));
    }

    @Test
    void usd_uses_western_grouping() {
        assertEquals("$75,000", MoneyFormatter.format(BigDecimal.valueOf(75_000), SupportedCurrency.USD));
        assertEquals("$1,234,567", MoneyFormatter.format(BigDecimal.valueOf(1_234_567), SupportedCurrency.USD));
    }

    @Test
    void eur_uses_ie_grouping() {
        assertEquals("€60,000", MoneyFormatter.format(BigDecimal.valueOf(60_000), SupportedCurrency.EUR));
    }

    @Test
    void negative_money_uses_minus_sign() {
        assertEquals("-₹7,53,161", MoneyFormatter.format(BigDecimal.valueOf(-753_161), SupportedCurrency.INR));
    }

    @Test
    void short_format_indian() {
        // Mirrors the JS toFixed(1) behaviour: "1.0 Cr" for exactly 1e7.
        assertEquals("₹1Cr", MoneyFormatter.formatShort(BigDecimal.valueOf(10_000_000), SupportedCurrency.INR));
        assertEquals("₹1.5Cr", MoneyFormatter.formatShort(BigDecimal.valueOf(15_000_000), SupportedCurrency.INR));
        assertEquals("₹1L", MoneyFormatter.formatShort(BigDecimal.valueOf(100_000), SupportedCurrency.INR));
    }

    @Test
    void short_format_western() {
        assertEquals("$1.23M", MoneyFormatter.formatShort(BigDecimal.valueOf(1_234_567), SupportedCurrency.USD));
        assertEquals("$60K", MoneyFormatter.formatShort(BigDecimal.valueOf(60_000), SupportedCurrency.USD));
    }
}
