package io.binarycodes.calculators.goal.ui;

import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.AxisType;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.component.charts.model.Configuration;
import com.vaadin.flow.component.charts.model.DataSeries;
import com.vaadin.flow.component.charts.model.DataSeriesItem;
import com.vaadin.flow.component.charts.model.Marker;
import com.vaadin.flow.component.charts.model.PlotOptionsLine;
import com.vaadin.flow.component.charts.model.XAxis;
import com.vaadin.flow.component.dependency.CssImport;
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.goal.domain.GoalResult;
import io.binarycodes.calculators.goal.domain.InvestmentSeries;
import io.binarycodes.calculators.goal.domain.MonthSnapshot;

import java.math.BigDecimal;
import java.util.List;

/**
 * Line chart of the corpus build-up split <em>per investment bucket</em> — one
 * line each — so the user can see how the individual buckets grow relative to
 * one another. Complements {@link GoalGrowthChart}, which shows the aggregate
 * principal/gains stack. Mirrors that chart's yearly-vs-monthly x-axis choice.
 *
 * <p>The host class scopes the categorical line palette in
 * {@code goal-per-investment-chart.css} so each line gets a distinct colour
 * instead of styled mode's near-identical default blues.</p>
 */
@CssImport(value = "./shadow/goal-per-investment-chart.css", themeFor = "vaadin-chart")
public class GoalPerInvestmentChart extends Chart {

    public GoalPerInvestmentChart() {
        super(ChartType.LINE);
        addClassName("goal-per-investment-chart");
        setWidthFull();
        setHeight("340px");

        final Configuration configuration = getConfiguration();
        configuration.setTitle(Translations.get("chart.goal.byInvestmentTitle"));
        configuration.getChart().setStyledMode(true);
        configuration.getLegend().setEnabled(true);

        final PlotOptionsLine lineOptions = new PlotOptionsLine();
        lineOptions.setMarker(new Marker(false));
        configuration.setPlotOptions(lineOptions);
    }

    public void update(GoalResult result, SupportedCurrency currency) {
        final Configuration configuration = getConfiguration();
        final List<MonthSnapshot> monthly = result.monthlySnapshots();
        final boolean useMonthly = monthly != null && !monthly.isEmpty();

        if (useMonthly) {
            renderMonthly(configuration, result);
        } else {
            renderYearly(configuration, result);
        }

        configuration.getyAxis().setTitle(currency.name());
        configuration.getyAxis().getLabels().setFormatter(MoneyFormatter.compactAxisFormatterJs(currency));
        drawChart(true);
    }

    private void renderYearly(Configuration configuration, GoalResult result) {
        final XAxis xAxis = configuration.getxAxis();
        xAxis.setTitle(Translations.get("chart.axis.year"));
        xAxis.setType(AxisType.LINEAR);
        xAxis.setCategories();

        final DataSeries[] series = new DataSeries[result.investmentSeries().size()];
        for (int index = 0; index < result.investmentSeries().size(); index++) {
            final InvestmentSeries bucket = result.investmentSeries().get(index);
            final DataSeries dataSeries = new DataSeries(seriesName(bucket, index));
            for (int rowIndex = 0; rowIndex < result.rows().size(); rowIndex++) {
                dataSeries.add(new DataSeriesItem(
                        result.rows().get(rowIndex).year(),
                        bucket.yearlyBalances().get(rowIndex).doubleValue()));
            }
            series[index] = dataSeries;
        }
        configuration.setSeries(series);
    }

    private void renderMonthly(Configuration configuration, GoalResult result) {
        final List<MonthSnapshot> monthly = result.monthlySnapshots();
        final String[] labels = monthly.stream().map(MonthSnapshot::label).toArray(String[]::new);

        final XAxis xAxis = configuration.getxAxis();
        xAxis.setTitle(Translations.get("chart.axis.month"));
        xAxis.setType(AxisType.CATEGORY);
        xAxis.setCategories(labels);

        final DataSeries[] series = new DataSeries[result.investmentSeries().size()];
        for (int index = 0; index < result.investmentSeries().size(); index++) {
            final InvestmentSeries bucket = result.investmentSeries().get(index);
            final DataSeries dataSeries = new DataSeries(seriesName(bucket, index));
            for (final BigDecimal balance : bucket.monthlyBalances()) {
                dataSeries.add(new DataSeriesItem("", balance.doubleValue()));
            }
            series[index] = dataSeries;
        }
        configuration.setSeries(series);
    }

    /** The bucket's label, falling back to a positional name when it's blank. */
    private static String seriesName(InvestmentSeries bucket, int index) {
        final String label = bucket.label();
        return label == null || label.isBlank()
                ? Translations.get("goal.investmentDefaultLabel", index + 1)
                : label;
    }
}
