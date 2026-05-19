package com.sujoy.calculators.base.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NumberToWordsTest {

    /* Golden strings taken from the live JS app's data-words-for output. */

    @Test
    void indian_zero() {
        assertEquals("Zero Rupees", words(0, Currency.INR));
    }

    @Test
    void indian_thousands() {
        assertEquals("Fifty Thousand Rupees",     words(50_000, Currency.INR));
        assertEquals("Twenty Five Thousand Rupees", words(25_000, Currency.INR));
    }

    @Test
    void indian_lakh() {
        assertEquals("One Lakh Fifty Thousand Rupees", words(150_000, Currency.INR));
        assertEquals("One Lakh Rupees",                words(100_000, Currency.INR));
    }

    @Test
    void indian_crore() {
        assertEquals("One Crore Fifty Lakh Rupees", words(15_000_000, Currency.INR));
        assertEquals(
            "Twelve Crore Thirty Four Lakh Fifty Six Thousand Seven Hundred Eighty Nine Rupees",
            words(123_456_789, Currency.INR));
    }

    @Test
    void usd_western() {
        assertEquals("Zero Dollars",                                  words(0, Currency.USD));
        assertEquals("Seventy Five Thousand Dollars",                 words(75_000, Currency.USD));
        assertEquals("Three Thousand Five Hundred Dollars",           words(3_500, Currency.USD));
        assertEquals("Five Hundred Dollars",                          words(500, Currency.USD));
        assertEquals(
            "One Million Two Hundred Thirty Four Thousand Five Hundred Sixty Seven Dollars",
            words(1_234_567, Currency.USD));
    }

    @Test
    void eur_western() {
        assertEquals("Sixty Thousand Euros",                  words(60_000, Currency.EUR));
        assertEquals("Two Thousand Five Hundred Euros",       words(2_500, Currency.EUR));
        assertEquals("One Thousand Five Hundred Euros",       words(1_500, Currency.EUR));
    }

    private static String words(long n, Currency c) {
        return NumberToWords.amountInWords(BigDecimal.valueOf(n), c);
    }
}
