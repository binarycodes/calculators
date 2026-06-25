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
 * (mirrors the retirement planner's cashflow columns). Given a yearly amount,
 * it shows the per-month value on the primary line and the yearly value on the
 * secondary line, each with an amount-in-words tooltip.
 */
public final class MoneyCells {

    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);

    private MoneyCells() {
    }

    /**
     * A {@link LitRenderer} rendering {@code yearlyAccessor}'s value as a
     * monthly figure (yearly ÷ 12) over the yearly figure. Currency is resolved
     * lazily so the cell re-renders correctly after a currency switch.
     */
    public static <T> LitRenderer<T> monthlyAndYearly(Function<T, BigDecimal> yearlyAccessor,
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
                .withProperty("monthly", row -> formatMonthly(yearlyAccessor.apply(row), currency.get()))
                .withProperty("yearly", row -> formatYearly(yearlyAccessor.apply(row), currency.get()))
                .withProperty("monthlyWords", row -> words(monthlyOf(yearlyAccessor.apply(row)), currency.get()))
                .withProperty("yearlyWords", row -> words(yearlyAccessor.apply(row), currency.get()));
    }

    private static String formatMonthly(BigDecimal yearlyAmount, SupportedCurrency currency) {
        if (yearlyAmount == null || yearlyAmount.signum() == 0) {
            return Translations.get("common.dash");
        }
        return MoneyFormatter.format(monthlyOf(yearlyAmount), currency) + " " + Translations.get("unit.perMonth");
    }

    private static String formatYearly(BigDecimal yearlyAmount, SupportedCurrency currency) {
        if (yearlyAmount == null || yearlyAmount.signum() == 0) {
            return "";
        }
        return MoneyFormatter.format(yearlyAmount, currency) + " " + Translations.get("unit.perYear");
    }

    private static BigDecimal monthlyOf(BigDecimal yearlyAmount) {
        if (yearlyAmount == null) {
            return BigDecimal.ZERO;
        }
        return yearlyAmount.divide(MONTHS_PER_YEAR, 0, RoundingMode.HALF_UP);
    }

    private static String words(BigDecimal amount, SupportedCurrency currency) {
        if (amount == null || amount.signum() == 0) {
            return "";
        }
        return NumberToWords.amountInWords(amount, currency);
    }
}
