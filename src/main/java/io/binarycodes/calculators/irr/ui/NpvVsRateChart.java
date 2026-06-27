package io.binarycodes.calculators.irr.ui;

import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.AxisType;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.component.charts.model.Configuration;
import com.vaadin.flow.component.charts.model.DataSeries;
import com.vaadin.flow.component.charts.model.DataSeriesItem;
import com.vaadin.flow.component.charts.model.Marker;
import com.vaadin.flow.component.charts.model.PlotLine;
import com.vaadin.flow.component.charts.model.PlotOptionsLine;
import com.vaadin.flow.component.charts.model.XAxis;
import com.vaadin.flow.component.charts.model.YAxis;
import com.vaadin.flow.component.dependency.CssImport;
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.irr.domain.NpvPoint;

import java.math.BigDecimal;
import java.util.List;

/**
 * The NPV-vs-discount-rate curve. NPV falls as the rate rises; where it crosses
 * zero is the IRR. A non-conventional schedule crosses zero more than once —
 * each crossing is marked with a vertical line, making a non-unique rate
 * visible rather than hidden behind a single headline number.
 */
@CssImport(value = "./shadow/npv-rate-chart.css", themeFor = "vaadin-chart")
public class NpvVsRateChart extends Chart {

    public NpvVsRateChart() {
        super(ChartType.LINE);
        addClassName("npv-rate-chart");
        setWidthFull();
        setHeight("340px");

        final Configuration configuration = getConfiguration();
        configuration.setTitle(Translations.get("chart.xirr.npvTitle"));
        configuration.getChart().setStyledMode(true);
        configuration.getLegend().setEnabled(false);

        final XAxis xAxis = configuration.getxAxis();
        xAxis.setType(AxisType.LINEAR);
        xAxis.setTitle(Translations.get("chart.axis.rate"));
        xAxis.getLabels().setFormatter("function() { return this.value + '%'; }");
    }

    public void update(List<NpvPoint> curve, List<BigDecimal> roots, SupportedCurrency currency) {
        final DataSeries series = new DataSeries(Translations.get("chart.xirr.series.npv"));
        final PlotOptionsLine options = new PlotOptionsLine();
        options.setMarker(new Marker(false));
        series.setPlotOptions(options);
        for (final NpvPoint point : curve) {
            series.add(new DataSeriesItem(asPercent(point.rate()), point.npv().doubleValue()));
        }

        final Configuration configuration = getConfiguration();

        final YAxis yAxis = configuration.getyAxis();
        yAxis.setTitle(Translations.get("chart.axis.npv"));
        yAxis.getLabels().setFormatter(MoneyFormatter.compactAxisFormatterJs(currency));
        yAxis.setPlotLines(zeroLine());

        configuration.getxAxis().setPlotLines(rootLines(roots));
        configuration.setSeries(series);
        drawChart(true);
    }

    private static PlotLine zeroLine() {
        final PlotLine line = new PlotLine();
        line.setValue(0);
        line.setClassName("npv-zero-line");
        line.setZIndex(3);
        return line;
    }

    private static PlotLine[] rootLines(List<BigDecimal> roots) {
        final PlotLine[] lines = new PlotLine[roots.size()];
        for (int index = 0; index < roots.size(); index++) {
            final PlotLine line = new PlotLine();
            line.setValue(asPercent(roots.get(index)));
            line.setClassName("npv-root-line");
            line.setZIndex(4);
            lines[index] = line;
        }
        return lines;
    }

    private static double asPercent(BigDecimal rate) {
        return rate.doubleValue() * 100.0;
    }
}
