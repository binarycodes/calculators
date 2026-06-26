package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.component.charts.Chart;
import io.binarycodes.calculators.buyrent.ui.BuyRentComparisonChart;
import io.binarycodes.calculators.loan.ui.LoanBalanceChart;
import io.binarycodes.calculators.loan.ui.LoanPaymentSplitChart;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every calculator chart shows its own heading so it reads on its own (the
 * investment chart, for instance, is titled "Corpus Build-Up"). These charts
 * previously shipped with a blank title; this guards against that regressing.
 */
class ChartHeadingsTest {

    @Test
    void loan_and_buyrent_charts_carry_a_non_blank_title() {
        assertHasTitle(LoanBalanceChart::new);
        assertHasTitle(LoanPaymentSplitChart::new);
        assertHasTitle(BuyRentComparisonChart::new);
    }

    private static void assertHasTitle(Supplier<? extends Chart> chartFactory) {
        final Chart chart = chartFactory.get();
        final var title = chart.getConfiguration().getTitle();
        assertNotNull(title, chart.getClass().getSimpleName() + " has no chart title");
        assertTrue(title.getText() != null && !title.getText().isBlank(),
                chart.getClass().getSimpleName() + " has a blank chart title");
    }
}
