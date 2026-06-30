package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.function.ValueProvider;
import com.vaadin.flow.data.renderer.LitRenderer;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The "monthly / yearly" projection-grid cell must derive its per-month figure
 * from the actual number of months the row aggregates, not a hard-coded 12.
 * Otherwise a partial final year (or any sub-12-month horizon) understates the
 * real monthly amount.
 */
class MoneyCellsTest {

    private record Row(BigDecimal periodTotal, int monthsInPeriod) {
    }

    private static String monthlyText(Row row) {
        final LitRenderer<Row> renderer = MoneyCells.monthlyAndYearly(
                Row::periodTotal, Row::monthsInPeriod, () -> SupportedCurrency.USD);
        final Map<String, ValueProvider<Row, ?>> providers = renderer.getValueProviders();
        return String.valueOf(providers.get("monthly").apply(row));
    }

    @Test
    void monthly_figure_divides_by_months_in_period_for_a_partial_year() {
        // A 6-month period totalling 600,000 was contributed at 100,000/month.
        final Row partial = new Row(new BigDecimal("600000"), 6);
        final String expected = MoneyFormatter.format(new BigDecimal("100000"), SupportedCurrency.USD)
                + " " + "unit.perMonth";
        assertEquals(expected, monthlyText(partial),
                "monthly figure must be period-total ÷ months-in-period, not ÷ 12");
    }

    @Test
    void monthly_figure_still_divides_by_twelve_for_a_full_year() {
        final Row fullYear = new Row(new BigDecimal("1200000"), 12);
        final String expected = MoneyFormatter.format(new BigDecimal("100000"), SupportedCurrency.USD)
                + " " + "unit.perMonth";
        assertEquals(expected, monthlyText(fullYear));
    }
}
