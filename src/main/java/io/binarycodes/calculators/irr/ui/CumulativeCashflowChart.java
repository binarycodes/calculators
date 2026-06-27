package io.binarycodes.calculators.irr.ui;

import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.AxisType;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.component.charts.model.Configuration;
import com.vaadin.flow.component.charts.model.DataSeries;
import com.vaadin.flow.component.charts.model.DataSeriesItem;
import com.vaadin.flow.component.charts.model.PlotLine;
import com.vaadin.flow.component.charts.model.XAxis;
import com.vaadin.flow.component.charts.model.YAxis;
import com.vaadin.flow.component.dependency.CssImport;
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.irr.domain.CashflowRow;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * The running (undiscounted) total over time. It starts negative as money is
 * paid in and climbs as money comes back; the point it first crosses zero — the
 * payback date — is marked with a vertical line.
 */
@CssImport(value = "./shadow/cumulative-cashflow-chart.css", themeFor = "vaadin-chart")
public class CumulativeCashflowChart extends Chart {

    public CumulativeCashflowChart() {
        super(ChartType.AREASPLINE);
        addClassName("cumulative-cashflow-chart");
        setWidthFull();
        setHeight("340px");

        final Configuration configuration = getConfiguration();
        configuration.setTitle(Translations.get("chart.xirr.cumulativeTitle"));
        configuration.getChart().setStyledMode(true);
        configuration.getLegend().setEnabled(false);

        final XAxis xAxis = configuration.getxAxis();
        xAxis.setType(AxisType.DATETIME);
        xAxis.setTitle(Translations.get("chart.axis.date"));
    }

    public void update(List<CashflowRow> rows, Optional<LocalDate> paybackDate, SupportedCurrency currency) {
        final DataSeries series = new DataSeries(Translations.get("chart.xirr.series.cumulative"));
        for (final CashflowRow row : rows) {
            series.add(new DataSeriesItem(epochMillis(row.date()), row.cumulative().doubleValue()));
        }

        final Configuration configuration = getConfiguration();
        final YAxis yAxis = configuration.getyAxis();
        yAxis.setTitle(currency.name());
        yAxis.getLabels().setFormatter(MoneyFormatter.compactAxisFormatterJs(currency));
        yAxis.setPlotLines(zeroLine());

        configuration.getxAxis().setPlotLines(paybackLine(paybackDate));
        configuration.setSeries(series);
        drawChart(true);
    }

    private static PlotLine zeroLine() {
        final PlotLine line = new PlotLine();
        line.setValue(0);
        line.setClassName("cumulative-zero-line");
        line.setZIndex(3);
        return line;
    }

    private static PlotLine[] paybackLine(Optional<LocalDate> paybackDate) {
        if (paybackDate.isEmpty()) {
            return new PlotLine[0];
        }
        final PlotLine line = new PlotLine();
        line.setValue(epochMillis(paybackDate.get()));
        line.setClassName("cumulative-payback-line");
        line.setZIndex(4);
        return new PlotLine[]{line};
    }

    private static long epochMillis(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }
}
