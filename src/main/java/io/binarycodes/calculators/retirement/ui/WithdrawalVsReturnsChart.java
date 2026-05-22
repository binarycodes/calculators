package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.component.charts.model.Configuration;
import com.vaadin.flow.component.charts.model.ListSeries;
import com.vaadin.flow.component.charts.model.PlotOptionsColumn;
import com.vaadin.flow.component.dependency.CssImport;
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
    private static final String RETURNS_CLASSNAME = "returns";
    private static final String WITHDRAWAL_CLASSNAME = "withdrawal";

    public WithdrawalVsReturnsChart() {
        super(ChartType.COLUMN);
        setWidthFull();
        setHeight("340px");

        final Configuration cfg = getConfiguration();
        cfg.setTitle("Withdrawal vs Returns");
        cfg.getChart().setStyledMode(true);
        cfg.getxAxis().setTitle("Age");
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

        final PlotOptionsColumn returnsOptions = new PlotOptionsColumn();
        returnsOptions.setClassName(RETURNS_CLASSNAME);

        final PlotOptionsColumn withdrawalOptions = new PlotOptionsColumn();
        withdrawalOptions.setClassName(WITHDRAWAL_CLASSNAME);

        final ListSeries returnsSeries = new ListSeries("Returns", returnsValues);
        returnsSeries.setPlotOptions(returnsOptions);

        final ListSeries withdrawalSeries = new ListSeries("Withdrawal", withdrawalValues);
        withdrawalSeries.setPlotOptions(withdrawalOptions);

        final Configuration cfg = getConfiguration();
        cfg.setSeries(returnsSeries, withdrawalSeries);
        cfg.getxAxis().setCategories(categories);
        cfg.getyAxis().setTitle(currency.name());
        cfg.getyAxis().getLabels().setFormatter(MoneyFormatter.compactAxisFormatterJs(currency));

        drawChart(true);
    }
}
