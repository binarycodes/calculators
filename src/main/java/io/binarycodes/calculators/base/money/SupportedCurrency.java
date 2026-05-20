package io.binarycodes.calculators.base.money;

import com.ibm.icu.util.ULocale;

/**
 * Supported display currencies. Mirrors the {@code CURRENCIES} table in the
 * original {@code retirement-calculator.js}.
 */
public enum SupportedCurrency {
    INR("₹", ULocale.forLanguageTag("en-IN"), "Rupees"),
    EUR("€", ULocale.forLanguageTag("en-IE"), "Euros"),
    USD("$", ULocale.forLanguageTag("en-US"), "Dollars");
    
    private final String symbol;
    private final ULocale locale;
    private final String word;

    SupportedCurrency(String symbol, ULocale locale, String word) {
        this.symbol = symbol;
        this.locale = locale;
        this.word = word;
    }

    public String symbol() {
        return this.symbol;
    }

    public ULocale locale() {
        return this.locale;
    }

    public String word() {
        return this.word;
    }
}
