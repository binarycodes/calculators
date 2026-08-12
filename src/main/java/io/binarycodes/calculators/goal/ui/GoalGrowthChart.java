package io.binarycodes.calculators.goal.ui;

import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.AxisType;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.component.charts.model.Configuration;
import com.vaadin.flow.component.charts.model.DataSeries;
import com.vaadin.flow.component.charts.model.DataSeriesItem;
import com.vaadin.flow.component.charts.model.PlotOptionsColumn;
import com.vaadin.flow.component.charts.model.Stacking;
import com.vaadin.flow.component.charts.model.XAxis;
import com.vaadin.flow.component.dependency.CssImport;
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.goal.domain.GoalProjectionRow;
import io.binarycodes.calculators.goal.domain.GoalResult;
import io.binarycodes.calculators.goal.domain.MonthSnapshot;

import java.util.List;

/**
 * Stacked column chart for the goal-planner corpus build-up — principal at the
 * bottom of each bar, gains stacked on top so the height equals the total
 * balance for that period.
 *
 * <p>For horizons shorter than three years the calculator hands back per-month
 * snapshots; this chart plots those against a category axis labelled
 * "Mon 'YY" so the user sees a meaningful trajectory rather than two or three
 * yearly checkpoints. Longer horizons use the yearly projection rows.</p>
 *
 * <p>Colour palette mirrors the retirement calculator's Return on Investments
 * chart (principal = primary, gains = success) via {@code goal-growth-chart.css}.</p>
 */
@CssImport(value = "./shadow/goal-growth-chart.css", themeFor = "vaadin-chart")
public class GoalGrowthChart extends Chart {

    public GoalGrowthChart() {
        super(ChartType.COLUMN);
        // Scopes the palette override in goal-growth-chart.css to this chart.
        addClassName("goal-growth-chart");
        setWidthFull();
        setHeight("340px");

        final Configuration configuration = getConfiguration();
        configuration.setTitle(Translations.get("chart.corpusBuildUp"));
        configuration.getChart().setStyledMode(true);
        configuration.getLegend().setEnabled(true);
    }

    public void update(GoalResult result, SupportedCurrency currency) {
        final Configuration configuration = getConfiguration();

        final List<MonthSnapshot> monthly = result.monthlySnapshots();
        if (monthly != null && !monthly.isEmpty()) {
            renderMonthly(configuration, monthly);
        } else {
            renderYearly(configuration, result);
        }

        // Highcharts stacks the *last* declared series at the bottom by default
        // for column charts; flip that so the declaration order matches the
        // visual stack (Principal first → bottom, Gains second → top).
        configuration.getyAxis().setReversedStacks(false);
        configuration.getyAxis().setTitle(currency.name());
        configuration.getyAxis().getLabels().setFormatter(MoneyFormatter.compactAxisFormatterJs(currency));

        drawChart(true);
    }

    private static void renderYearly(Configuration configuration, GoalResult result) {
        final XAxis xAxis = configuration.getxAxis();
        xAxis.setTitle(Translations.get("chart.axis.year"));
        xAxis.setType(AxisType.LINEAR);

        // Series order fixes the palette index the CSS recolours: 0 = principal, 1 = gains.
        final DataSeries principalSeries = stackedSeries(Translations.get("chart.series.principal"));
        final DataSeries gainsSeries = stackedSeries(Translations.get("chart.series.gains"));
        for (final GoalProjectionRow row : result.rows()) {
            principalSeries.add(new DataSeriesItem(row.year(), row.principal().doubleValue()));
            gainsSeries.add(new DataSeriesItem(row.year(), row.gains().doubleValue()));
        }
        configuration.setSeries(principalSeries, gainsSeries);
    }

    private static void renderMonthly(Configuration configuration, List<MonthSnapshot> monthly) {
        final String[] labels = monthly.stream()
                .map(MonthSnapshot::label)
                .toArray(String[]::new);

        final XAxis xAxis = configuration.getxAxis();
        xAxis.setTitle(Translations.get("chart.axis.month"));
        xAxis.setType(AxisType.CATEGORY);
        xAxis.setCategories(labels);

        final DataSeries principalSeries = stackedSeries(Translations.get("chart.series.principal"));
        final DataSeries gainsSeries = stackedSeries(Translations.get("chart.series.gains"));
        for (final MonthSnapshot snapshot : monthly) {
            principalSeries.add(new DataSeriesItem("", snapshot.principal().doubleValue()));
            gainsSeries.add(new DataSeriesItem("", snapshot.gains().doubleValue()));
        }
        configuration.setSeries(principalSeries, gainsSeries);
    }

    private static DataSeries stackedSeries(String name) {
        final DataSeries series = new DataSeries(name);
        final PlotOptionsColumn options = new PlotOptionsColumn();
        options.setStacking(Stacking.NORMAL);
        series.setPlotOptions(options);
        return series;
    }
}
