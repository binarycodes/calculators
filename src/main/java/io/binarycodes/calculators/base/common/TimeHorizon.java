package io.binarycodes.calculators.base.common;

import java.time.LocalDate;

/**
 * Resolves a {@link TimeHorizonMode} plus its mode-specific fields into a total
 * month count. Shared by every calculator that projects over a horizon so the
 * Years/Ages/Target-Year semantics stay identical across the app.
 */
public final class TimeHorizon {

    private TimeHorizon() {
    }

    /**
     * Convert the chosen horizon mode into a total number of months.
     *
     * @param mode        which set of fields drives the deadline (defaults to {@code YEARS} if null)
     * @param years       whole years (YEARS mode)
     * @param months      extra months 0–11 (YEARS mode; null treated as 0)
     * @param currentAge  current age (AGES mode)
     * @param goalAge     target age (AGES mode)
     * @param targetYear  target calendar year (TARGET_YEAR mode)
     * @param targetMonth target month 1–12 (TARGET_YEAR mode; null treated as the current month)
     * @return total horizon length in months
     * @throws IllegalArgumentException if a required field for the chosen mode is missing or out of range
     */
    public static int resolveTotalMonths(TimeHorizonMode mode, Integer years, Integer months,
                                         Integer currentAge, Integer goalAge,
                                         Integer targetYear, Integer targetMonth) {
        final TimeHorizonMode resolved = mode == null ? TimeHorizonMode.YEARS : mode;
        return switch (resolved) {
            case YEARS -> {
                final int wholeYears = required(years, "Years to goal");
                final int extraMonths = months == null ? 0 : months;
                if (extraMonths < 0 || extraMonths > 11) {
                    throw new IllegalArgumentException("Months must be between 0 and 11.");
                }
                yield wholeYears * 12 + extraMonths;
            }
            case AGES -> {
                final int from = required(currentAge, "Current age");
                final int to = required(goalAge, "Goal age");
                yield (to - from) * 12;
            }
            case TARGET_YEAR -> {
                final int year = required(targetYear, "Target year");
                final int month = targetMonth == null ? LocalDate.now().getMonthValue() : targetMonth;
                if (month < 1 || month > 12) {
                    throw new IllegalArgumentException("Target month must be between 1 and 12.");
                }
                final LocalDate today = LocalDate.now();
                yield (year - today.getYear()) * 12 + (month - today.getMonthValue());
            }
        };
    }

    private static int required(Integer value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value;
    }
}
