package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.charts.model.DataSeries;
import com.vaadin.flow.component.charts.model.DataSeriesItem;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.retirement.domain.ProjectionRow;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;
import io.binarycodes.calculators.retirement.domain.RetirementResult;
import io.binarycodes.calculators.retirement.domain.TimelineEvent;
import io.binarycodes.calculators.retirement.domain.TimelineEventType;
import io.binarycodes.calculators.retirement.domain.TimelineYear;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every per-year value chart shares one x-axis convention: a row for the year
 * the person is age N is plotted at the year-end age N+1, so reading an age
 * across the corpus / expenses / returns / withdrawal tabs always refers to the
 * same year. The event timeline is exempt — it marks events at their actual age.
 */
class RetirementChartAxisTest {

    private static ProjectionRow row(int age, boolean post, double annualExp, double endCorpus) {
        return new ProjectionRow(2000 + age, age, false, post,
                BigDecimal.valueOf(annualExp), BigDecimal.valueOf(endCorpus),
                BigDecimal.TEN, BigDecimal.valueOf(50), BigDecimal.valueOf(post ? 40 : 0),
                BigDecimal.ZERO, BigDecimal.valueOf(endCorpus), false);
    }

    private static RetirementResult result() {
        return new RetirementResult(
                List.of(row(60, false, 100, 1000), row(61, true, 110, 1100), row(62, true, 120, 900)),
                Optional.empty(), new BigDecimal("1000"));
    }

    private static RetirementInputs inputs() {
        final RetirementInputs inputs = new RetirementInputs();
        inputs.setCurrentAge(60);
        inputs.setRetireAge(61);
        inputs.setCorpus(new BigDecimal("1000"));
        inputs.setInflationPct(BigDecimal.ZERO);
        return inputs;
    }

    @Test
    void expenses_chart_labels_rows_by_year_end_age() {
        final ExpensesChart chart = new ExpensesChart();
        chart.update(result(), SupportedCurrency.USD);
        assertArrayEquals(new String[]{"61", "62", "63"},
                chart.getConfiguration().getxAxis().getCategories());
    }

    @Test
    void return_on_investments_chart_labels_rows_by_year_end_age() {
        final ReturnOnInvestmentsChart chart = new ReturnOnInvestmentsChart();
        chart.update(inputs(), result(), SupportedCurrency.USD);
        assertArrayEquals(new String[]{"61", "62", "63"},
                chart.getConfiguration().getxAxis().getCategories());
    }

    @Test
    void withdrawal_vs_returns_chart_labels_post_rows_by_year_end_age() {
        final WithdrawalVsReturnsChart chart = new WithdrawalVsReturnsChart();
        chart.update(result(), SupportedCurrency.USD);
        assertArrayEquals(new String[]{"62", "63"},
                chart.getConfiguration().getxAxis().getCategories());
    }

    @Test
    void corpus_chart_plots_rows_at_year_end_age() {
        final CorpusChart chart = new CorpusChart();
        chart.update(inputs(), result(), SupportedCurrency.USD);
        final DataSeries series = (DataSeries) chart.getConfiguration().getSeries().get(0);
        final List<DataSeriesItem> data = series.getData();
        // Seed point at the current age, then each row at its year-end age.
        assertEquals(60, data.get(0).getX().intValue());
        assertEquals(61, data.get(1).getX().intValue());
        assertEquals(62, data.get(2).getX().intValue());
        assertEquals(63, data.get(3).getX().intValue());
    }

    @Test
    void timeline_chart_marks_events_at_their_actual_age() {
        final TimelineChart chart = new TimelineChart();
        chart.update(List.of(new TimelineYear(61, 2061,
                List.of(TimelineEvent.of(TimelineEventType.RETIREMENT)))), SupportedCurrency.USD);
        final DataSeries series = (DataSeries) chart.getConfiguration().getSeries().get(0);
        assertEquals(61, series.getData().get(0).getX().intValue(),
                "timeline events stay at their actual age, not year-end");
    }
}
