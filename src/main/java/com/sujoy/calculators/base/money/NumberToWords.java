package com.sujoy.calculators.base.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts whole numbers to English words using either the Indian numbering
 * system (Lakh / Crore) or the Western system (Thousand / Million / Billion /
 * Trillion). Mirrors the {@code numberToWordsIndian} and
 * {@code numberToWordsWestern} helpers in {@code retirement-calculator.js}.
 */
public final class NumberToWords {

    private NumberToWords() {}

    private static final String[] ONES = {
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
        "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen",
        "Sixteen", "Seventeen", "Eighteen", "Nineteen"
    };
    private static final String[] TENS = {
        "", "", "Twenty", "Thirty", "Forty", "Fifty",
        "Sixty", "Seventy", "Eighty", "Ninety"
    };

    /** Convenience: "{words} {Currency.word()}", e.g. "Fifty Thousand Rupees". */
    public static String amountInWords(BigDecimal value, Currency currency) {
        if (value == null) value = BigDecimal.ZERO;
        long n = value.setScale(0, RoundingMode.HALF_UP).longValueExact();
        String words = (currency.style() == Currency.Style.INDIAN)
                ? indian(n)
                : western(n);
        return words + " " + currency.word();
    }

    public static String indian(long n) {
        if (n == 0) return "Zero";
        if (n < 0)  return "Minus " + indian(-n);
        return indianHelper(n);
    }

    public static String western(long n) {
        if (n == 0) return "Zero";
        if (n < 0)  return "Minus " + western(-n);
        return westernHelper(n);
    }

    private static String indianHelper(long n) {
        if (n < 100)  return twoDigits((int) n);
        if (n < 1000) return threeDigits((int) n);
        if (n < 100_000L) {
            long t = n / 1000, r = n % 1000;
            return twoDigits((int) t) + " Thousand" + (r > 0 ? " " + threeDigits((int) r) : "");
        }
        if (n < 10_000_000L) {
            long l = n / 100_000L, r = n % 100_000L;
            return twoDigits((int) l) + " Lakh" + (r > 0 ? " " + indianHelper(r) : "");
        }
        long c = n / 10_000_000L, r = n % 10_000_000L;
        return indianHelper(c) + " Crore" + (r > 0 ? " " + indianHelper(r) : "");
    }

    private static String westernHelper(long n) {
        if (n < 1000) return threeDigits((int) n);
        long[][] scales = {
            { 1_000_000_000_000L, 0 }, // Trillion
            { 1_000_000_000L,     1 }, // Billion
            { 1_000_000L,         2 }, // Million
            { 1_000L,             3 }  // Thousand
        };
        String[] names = { "Trillion", "Billion", "Million", "Thousand" };
        for (long[] scale : scales) {
            long v = scale[0];
            if (n >= v) {
                long q = n / v, r = n % v;
                return westernHelper(q) + " " + names[(int) scale[1]] +
                        (r > 0 ? " " + westernHelper(r) : "");
            }
        }
        return threeDigits((int) n);
    }

    private static String twoDigits(int n) {
        if (n < 20) return ONES[n];
        int t = n / 10, o = n % 10;
        return TENS[t] + (o > 0 ? " " + ONES[o] : "");
    }

    private static String threeDigits(int n) {
        int h = n / 100, r = n % 100;
        StringBuilder out = new StringBuilder();
        if (h > 0) out.append(ONES[h]).append(" Hundred");
        if (r > 0) {
            if (out.length() > 0) out.append(' ');
            out.append(twoDigits(r));
        }
        return out.toString();
    }
}
