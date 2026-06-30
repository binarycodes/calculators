package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.data.renderer.LitRenderer;
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.NumberToWords;
import io.binarycodes.calculators.base.money.SupportedCurrency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Builds the two-line "monthly / yearly" money cell used in projection grids
 * (mirrors the retirement planner's cashflow columns). Given a period total, it
 * shows the per-month value on the primary line and the period total on the
 * secondary line, each with an amount-in-words tooltip. The per-month figure
 * divides by the months the row actually aggregates, so a partial final year
 * (or any sub-12-month horizon) reports the true monthly amount rather than an
 * understated total ÷ 12.
 */
public final class MoneyCells {

    private MoneyCells() {
    }

    /**
     * A {@link LitRenderer} rendering {@code periodTotalAccessor}'s value as a
     * monthly figure (period total ÷ {@code monthsAccessor}) over the period
     * total. Currency is resolved lazily so the cell re-renders correctly after
     * a currency switch.
     */
    public static <T> LitRenderer<T> monthlyAndYearly(Function<T, BigDecimal> periodTotalAccessor,
                                                      Function<T, Integer> monthsAccessor,
                                                      Supplier<SupportedCurrency> currency) {
        return LitRenderer.<T>of(
                        """
                        <div class="money-cell">
                            <div id="monthly_${item.id}" class="money-cell-primary">${item.monthly}</div>
                            <div id="yearly_${item.id}" class="money-cell-secondary">${item.yearly}</div>
                        </div>
                        <vaadin-tooltip for="monthly_${item.id}" text="${item.monthlyWords}"></vaadin-tooltip>
                        <vaadin-tooltip for="yearly_${item.id}" text="${item.yearlyWords}"></vaadin-tooltip>
                        """)
                .withProperty("id", row -> UUID.randomUUID().toString())
                .withProperty("monthly", row -> formatMonthly(periodTotalAccessor.apply(row), monthsAccessor.apply(row), currency.get()))
                .withProperty("yearly", row -> formatYearly(periodTotalAccessor.apply(row), currency.get()))
                .withProperty("monthlyWords", row -> words(monthlyOf(periodTotalAccessor.apply(row), monthsAccessor.apply(row)), currency.get()))
                .withProperty("yearlyWords", row -> words(periodTotalAccessor.apply(row), currency.get()));
    }

    private static String formatMonthly(BigDecimal periodTotal, int monthsInPeriod, SupportedCurrency currency) {
        if (periodTotal == null || periodTotal.signum() == 0) {
            return Translations.get("common.dash");
        }
        return MoneyFormatter.format(monthlyOf(periodTotal, monthsInPeriod), currency) + " " + Translations.get("unit.perMonth");
    }

    private static String formatYearly(BigDecimal periodTotal, SupportedCurrency currency) {
        if (periodTotal == null || periodTotal.signum() == 0) {
            return "";
        }
        return MoneyFormatter.format(periodTotal, currency) + " " + Translations.get("unit.perYear");
    }

    private static BigDecimal monthlyOf(BigDecimal periodTotal, int monthsInPeriod) {
        if (periodTotal == null || monthsInPeriod <= 0) {
            return BigDecimal.ZERO;
        }
        return periodTotal.divide(BigDecimal.valueOf(monthsInPeriod), 0, RoundingMode.HALF_UP);
    }

    private static String words(BigDecimal amount, SupportedCurrency currency) {
        if (amount == null || amount.signum() == 0) {
            return "";
        }
        return NumberToWords.amountInWords(amount, currency);
    }
}
