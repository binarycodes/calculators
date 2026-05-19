package com.sujoy.calculators.base.money;

import java.util.Locale;

/**
 * Supported display currencies. Mirrors the {@code CURRENCIES} table in the
 * original {@code retirement-calculator.js}.
 */
public enum Currency {
    INR("₹", Locale.forLanguageTag("en-IN"), Style.INDIAN,  "Rupees"),
    EUR("€", Locale.GERMANY,                 Style.WESTERN, "Euros"),
    USD("$", Locale.US,                      Style.WESTERN, "Dollars");

    public enum Style { INDIAN, WESTERN }

    private final String symbol;
    private final Locale locale;
    private final Style style;
    private final String word;

    Currency(String symbol, Locale locale, Style style, String word) {
        this.symbol = symbol;
        this.locale = locale;
        this.style  = style;
        this.word   = word;
    }

    public String symbol()  { return symbol; }
    public Locale locale()  { return locale; }
    public Style  style()   { return style; }
    public String word()    { return word; }
}
