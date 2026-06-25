package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.component.charts.model.Configuration;
import com.vaadin.flow.component.charts.model.ListSeries;
import com.vaadin.flow.component.charts.model.PlotOptionsColumn;
import com.vaadin.flow.component.dependency.CssImport;
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.retirement.domain.ProjectionRow;
import io.binarycodes.calculators.retirement.domain.RetirementResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Grouped column chart comparing annual returns against annual withdrawals
 * for each post-retirement year. Bars where returns meet or exceed withdrawal
 * indicate a self-sustaining year; shortfalls show the corpus being drawn
 * down.
 */
@CssImport(value = "./shadow/withdrawal-vs-returns-chart.css", themeFor = "vaadin-chart")
public class WithdrawalVsReturnsChart extends Chart {

    public WithdrawalVsReturnsChart() {
        super(ChartType.COLUMN);
        // Scopes the palette override in withdrawal-vs-returns-chart.css to this chart.
        addClassName("withdrawal-vs-returns-chart");
        setWidthFull();
        setHeight("340px");

        final Configuration cfg = getConfiguration();
        cfg.setTitle(Translations.get("chart.retirement.withdrawalVsReturns"));
        cfg.getChart().setStyledMode(true);
        cfg.getxAxis().setTitle(Translations.get("chart.axis.age"));
        cfg.getLegend().setEnabled(true);
    }

    public void update(RetirementResult result, SupportedCurrency currency) {
        final List<ProjectionRow> postRetirementRows = new ArrayList<>();
        for (final ProjectionRow row : result.rows()) {
            if (row.isPost()) {
                postRetirementRows.add(row);
            }
        }

        final Number[] returnsValues = new Number[postRetirementRows.size()];
        final Number[] withdrawalValues = new Number[postRetirementRows.size()];
        final String[] categories = new String[postRetirementRows.size()];
        for (int index = 0; index < postRetirementRows.size(); index++) {
            final ProjectionRow row = postRetirementRows.get(index);
            returnsValues[index] = row.returns().doubleValue();
            withdrawalValues[index] = row.withdrawal().doubleValue();
            categories[index] = Integer.toString(row.age());
        }

        // Series order fixes the palette index the CSS recolours: 0 = returns, 1 = withdrawal.
        final ListSeries returnsSeries = new ListSeries(Translations.get("chart.series.returns"), returnsValues);
        returnsSeries.setPlotOptions(new PlotOptionsColumn());

        final ListSeries withdrawalSeries = new ListSeries(Translations.get("chart.series.withdrawal"), withdrawalValues);
        withdrawalSeries.setPlotOptions(new PlotOptionsColumn());

        final Configuration cfg = getConfiguration();
        cfg.setSeries(returnsSeries, withdrawalSeries);
        cfg.getxAxis().setCategories(categories);
        cfg.getyAxis().setTitle(currency.name());
        cfg.getyAxis().getLabels().setFormatter(MoneyFormatter.compactAxisFormatterJs(currency));

        drawChart(true);
    }
}
