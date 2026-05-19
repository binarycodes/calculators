package com.sujoy.calculators.base.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;

/**
 * Locale-aware money formatting. Mirrors the {@code fmt} and {@code fmtShort}
 * helpers in {@code retirement-calculator.js}.
 */
public final class MoneyFormatter {

    private MoneyFormatter() {}

    /** "₹12,34,56,789" / "$123,456,789" / "€60.000" — rounds half-up, no decimals. */
    public static String format(BigDecimal value, Currency currency) {
        if (value == null) value = BigDecimal.ZERO;
        long n = value.setScale(0, RoundingMode.HALF_UP).longValueExact();
        boolean neg = n < 0;
        long abs = neg ? -n : n;
        String digits = (currency.style() == Currency.Style.INDIAN)
                ? groupIndian(abs)
                : groupWestern(abs, currency.locale());
        return (neg ? "−" : "") + currency.symbol() + digits;
    }

    /** Indian grouping: last 3 digits, then pairs (e.g. 12345678 → "1,23,45,678"). */
    static String groupIndian(long n) {
        String s = Long.toString(n);
        if (s.length() <= 3) return s;
        String last3 = s.substring(s.length() - 3);
        String head  = s.substring(0, s.length() - 3);
        // Group head from the RIGHT into pairs.
        StringBuilder grouped = new StringBuilder();
        int i = head.length();
        while (i > 2) {
            grouped.insert(0, "," + head.substring(i - 2, i));
            i -= 2;
        }
        if (i > 0) grouped.insert(0, head.substring(0, i));
        return grouped + "," + last3;
    }

    /** Western grouping using the locale's NumberFormat (e.g. en-US "1,234,567", de "1.234.567"). */
    static String groupWestern(long n, java.util.Locale locale) {
        NumberFormat nf = NumberFormat.getNumberInstance(locale);
        nf.setMaximumFractionDigits(0);
        return nf.format(n);
    }

    /** Compact: Indian → Cr/L/k; Western → B/M/k. Same thresholds as the JS app. */
    public static String formatShort(BigDecimal value, Currency currency) {
        if (value == null) value = BigDecimal.ZERO;
        double n = value.doubleValue();
        String s = currency.symbol();
        if (currency.style() == Currency.Style.INDIAN) {
            if (n >= 1e7)  return s + trim(n / 1e7,  n >= 1e8 ? 0 : 1) + " Cr";
            if (n >= 1e5)  return s + trim(n / 1e5,  n >= 1e6 ? 0 : 1) + " L";
            if (n >= 1000) return s + trim(n / 1000, 0) + "k";
            return s + Math.round(n);
        }
        if (n >= 1e9) return s + trim(n / 1e9, 1) + "B";
        if (n >= 1e6) return s + trim(n / 1e6, 1) + "M";
        if (n >= 1e3) return s + trim(n / 1e3, 1) + "k";
        return s + Math.round(n);
    }

    private static String trim(double v, int decimals) {
        if (decimals == 0) return Long.toString(Math.round(v));
        return String.format("%." + decimals + "f", v);
    }
}
