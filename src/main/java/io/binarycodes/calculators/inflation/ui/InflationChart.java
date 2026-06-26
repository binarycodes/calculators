package io.binarycodes.calculators.inflation.ui;

import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.component.charts.model.Configuration;
import com.vaadin.flow.component.charts.model.DataSeries;
import com.vaadin.flow.component.charts.model.DataSeriesItem;
import com.vaadin.flow.component.charts.model.Marker;
import com.vaadin.flow.component.charts.model.PlotOptionsAreaspline;
import com.vaadin.flow.component.dependency.CssImport;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.inflation.domain.InflationPoint;
import io.binarycodes.calculators.inflation.domain.InflationResult;

/**
 * Area-spline showing how the amount drifts year by year under inflation. A
 * single neutral series — there's no good/bad outcome here, just the visual
 * effect of compounding inflation across the horizon.
 */
@CssImport(value = "./shadow/inflation-chart.css", themeFor = "vaadin-chart")
public class InflationChart extends Chart {

    public InflationChart() {
        super(ChartType.AREASPLINE);
        addClassName("inflation-chart");
        setWidthFull();
        setHeight("340px");

        final Configuration configuration = getConfiguration();
        configuration.setTitle(getTranslation("chart.inflation.title"));
        configuration.getChart().setStyledMode(true);
        configuration.getxAxis().setTitle(getTranslation("chart.axis.year"));
        // Years are whole numbers — suppress fractional ticks like "2026.25".
        configuration.getxAxis().setAllowDecimals(false);
        configuration.getLegend().setEnabled(false);
    }

    public void update(InflationResult result, SupportedCurrency currency) {
        final DataSeries series = new DataSeries(getTranslation("chart.inflation.series"));
        for (final InflationPoint point : result.progression()) {
            series.add(new DataSeriesItem(point.year(), point.value().doubleValue()));
        }

        final PlotOptionsAreaspline plotOptions = new PlotOptionsAreaspline();
        plotOptions.setMarker(new Marker(false));

        final Configuration configuration = getConfiguration();
        configuration.getyAxis().setTitle(currency.name());
        configuration.getyAxis().getLabels().setFormatter(MoneyFormatter.compactAxisFormatterJs(currency));
        configuration.setSeries(series);
        configuration.setPlotOptions(plotOptions);

        drawChart(true);
    }
}
