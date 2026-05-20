package io.binarycodes.calculators.base.money;

import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.text.RuleBasedNumberFormat;

import java.math.BigDecimal;

/**
 * Converts whole numbers to English words using either the Indian numbering
 * system (Lakh / Crore) or the Western system (Thousand / Million / Billion /
 * Trillion). Mirrors the {@code numberToWordsIndian} and
 * {@code numberToWordsWestern} helpers in {@code retirement-calculator.js}.
 */
public final class NumberToWords {

    private NumberToWords() {
    }

    /**
     * Convenience: "{words} {Currency.word()}", e.g. "Fifty Thousand Rupees".
     */
    public static String amountInWords(BigDecimal value, SupportedCurrency supportedCurrency) {
        final var locale = supportedCurrency.locale().toLocale();
        final var formatter = new RuleBasedNumberFormat(supportedCurrency.locale(), RuleBasedNumberFormat.SPELLOUT);
        final var words = formatter.format(value == null ? BigDecimal.ZERO : value);

        return UCharacter.toTitleCase(locale,
                words.toLowerCase(),
                BreakIterator.getWordInstance(locale),
                0
        ) + " " + supportedCurrency.word();
    }

}
