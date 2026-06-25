package io.binarycodes.calculators.investment.ui;

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
import io.binarycodes.calculators.investment.domain.InvestmentResult;
import io.binarycodes.calculators.investment.domain.InvestmentYear;

/**
 * Stacked column chart of the corpus build-up — principal at the bottom, gains
 * stacked on top. Principal flattens once contributions stop, so the
 * investment-vs-hold split is visible in the bars. Palette matches the goal
 * planner's chart (principal = primary, gains = success).
 */
@CssImport(value = "./shadow/investment-growth-chart.css", themeFor = "vaadin-chart")
public class InvestmentGrowthChart extends Chart {

    public InvestmentGrowthChart() {
        super(ChartType.COLUMN);
        // Scopes the palette override in investment-growth-chart.css to this chart.
        addClassName("investment-growth-chart");
        setWidthFull();
        setHeight("340px");

        final Configuration configuration = getConfiguration();
        configuration.setTitle(Translations.get("chart.corpusBuildUp"));
        configuration.getChart().setStyledMode(true);
        configuration.getLegend().setEnabled(true);
        final XAxis xAxis = configuration.getxAxis();
        xAxis.setTitle(Translations.get("chart.axis.year"));
        xAxis.setType(AxisType.LINEAR);
        xAxis.setAllowDecimals(false);
    }

    public void update(InvestmentResult result, SupportedCurrency currency) {
        // Series order fixes the palette index the CSS recolours: 0 = principal, 1 = gains.
        final DataSeries principalSeries = stackedSeries(Translations.get("chart.series.principal"));
        final DataSeries gainsSeries = stackedSeries(Translations.get("chart.series.gains"));
        for (final InvestmentYear row : result.rows()) {
            principalSeries.add(new DataSeriesItem(row.year(), row.principal().doubleValue()));
            gainsSeries.add(new DataSeriesItem(row.year(), row.gains().doubleValue()));
        }

        final Configuration configuration = getConfiguration();
        configuration.getyAxis().setReversedStacks(false);
        configuration.getyAxis().setTitle(currency.name());
        configuration.getyAxis().getLabels().setFormatter(MoneyFormatter.compactAxisFormatterJs(currency));
        configuration.setSeries(principalSeries, gainsSeries);

        drawChart(true);
    }

    private static DataSeries stackedSeries(String name) {
        final DataSeries series = new DataSeries(name);
        final PlotOptionsColumn options = new PlotOptionsColumn();
        options.setStacking(Stacking.NORMAL);
        series.setPlotOptions(options);
        return series;
    }
}
