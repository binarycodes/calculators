package io.binarycodes.calculators.base.money;

import com.ibm.icu.number.Notation;
import com.ibm.icu.number.NumberFormatter;
import com.ibm.icu.number.Precision;
import com.ibm.icu.util.Currency;

import java.math.BigDecimal;

/**
 * Locale-aware money formatting. Mirrors the {@code fmt} and {@code fmtShort}
 * helpers in {@code retirement-calculator.js}.
 */
public final class MoneyFormatter {

    private MoneyFormatter() {
    }

    /**
     * "₹12,34,56,789" / "$123,456,789" / "€60.000" — rounds half-up, no decimals.
     */
    public static String format(BigDecimal value, SupportedCurrency supportedCurrency) {
        return NumberFormatter.with()
                .notation(Notation.simple())
                .unit(Currency.getInstance(supportedCurrency.locale()))
                .precision(Precision.maxFraction(0))
                .locale(supportedCurrency.locale())
                .format(value == null ? BigDecimal.ZERO : value)
                .toString();
    }

    /**
     * Compact: Indian → Cr/L/k; Western → B/M/k. Same thresholds as the JS app.
     */
    public static String formatShort(BigDecimal value, SupportedCurrency supportedCurrency) {
        return NumberFormatter.with()
                .notation(Notation.compactShort())
                .unit(Currency.getInstance(supportedCurrency.locale()))
                .precision(Precision.maxFraction(2))
                .locale(supportedCurrency.locale())
                .format(value)
                .toString();
    }

    /**
     * JS function (as a string) suitable for {@code Highcharts} axis labels.
     * Uses the browser's {@code Intl.NumberFormat} with compact notation —
     * the browser implementation is backed by the same CLDR / ICU data this
     * class uses server-side, so the output matches {@link #formatShort}
     * (Indian → L / Cr, Western → K / M / B / T) for the currency's locale.
     */
    public static String compactAxisFormatterJs(SupportedCurrency supportedCurrency) {
        return """
                function() {
                    const formatter = new Intl.NumberFormat('%s', {
                        notation: 'compact',
                        maximumFractionDigits: 1,
                        style: 'currency',
                        currency: '%s',
                        currencyDisplay: 'narrowSymbol'
                    });
                    return formatter.format(this.value);
                }
                """.formatted(
                supportedCurrency.locale().toLanguageTag(),
                supportedCurrency.name());
    }
}
