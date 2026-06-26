package io.binarycodes.calculators.inflation.ui;

import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.AxisType;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.component.charts.model.Configuration;
import com.vaadin.flow.component.charts.model.DataSeries;
import com.vaadin.flow.component.charts.model.DataSeriesItem;
import com.vaadin.flow.component.charts.model.Marker;
import com.vaadin.flow.component.charts.model.PlotOptionsArearange;
import com.vaadin.flow.component.charts.model.PlotOptionsLine;
import com.vaadin.flow.component.charts.model.XAxis;
import com.vaadin.flow.component.dependency.CssImport;
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.inflation.domain.InflationBand;
import io.binarycodes.calculators.inflation.domain.InflationPoint;
import io.binarycodes.calculators.inflation.domain.InflationResult;

import java.math.BigDecimal;

/**
 * Area-range chart of the projected value under an uncertain inflation rate: a
 * shaded band between the low (rate − variation) and high (rate + variation)
 * trajectories, with the central "expected" line on top. When the variation is
 * zero the band collapses onto the line.
 */
@CssImport(value = "./shadow/inflation-band-chart.css", themeFor = "vaadin-chart")
public class InflationBandChart extends Chart {

    public InflationBandChart() {
        super(ChartType.AREARANGE);
        addClassName("inflation-band-chart");
        setWidthFull();
        setHeight("340px");

        final Configuration configuration = getConfiguration();
        configuration.setTitle(Translations.get("chart.inflation.bandTitle"));
        configuration.getChart().setStyledMode(true);
        configuration.getLegend().setEnabled(true);
        final XAxis xAxis = configuration.getxAxis();
        xAxis.setTitle(Translations.get("chart.axis.year"));
        xAxis.setAllowDecimals(false);
    }

    public void update(InflationResult result, SupportedCurrency currency, BigDecimal variationPct) {
        final DataSeries bandSeries = new DataSeries(rangeSeriesName(variationPct));
        for (final InflationBand point : result.band()) {
            final DataSeriesItem item = new DataSeriesItem();
            item.setX(point.year());
            item.setLow(point.low().doubleValue());
            item.setHigh(point.high().doubleValue());
            bandSeries.add(item);
        }
        final PlotOptionsArearange bandOptions = new PlotOptionsArearange();
        bandOptions.setLineWidth(0);
        bandSeries.setPlotOptions(bandOptions);

        final DataSeries centralSeries = new DataSeries(Translations.get("chart.inflation.expectedSeries"));
        for (final InflationPoint point : result.progression()) {
            centralSeries.add(new DataSeriesItem(point.year(), point.value().doubleValue()));
        }
        final PlotOptionsLine lineOptions = new PlotOptionsLine();
        lineOptions.setMarker(new Marker(false));
        centralSeries.setPlotOptions(lineOptions);

        final Configuration configuration = getConfiguration();
        configuration.getyAxis().setTitle(currency.name());
        configuration.getyAxis().getLabels().setFormatter(MoneyFormatter.compactAxisFormatterJs(currency));
        configuration.setSeries(bandSeries, centralSeries);

        drawChart(true);
    }

    private static String rangeSeriesName(BigDecimal variationPct) {
        final BigDecimal variation = variationPct == null ? BigDecimal.ZERO : variationPct.stripTrailingZeros();
        return Translations.get("chart.inflation.rangeSeries", variation.toPlainString());
    }
}
