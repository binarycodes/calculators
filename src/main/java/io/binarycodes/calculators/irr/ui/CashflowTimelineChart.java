package io.binarycodes.calculators.irr.ui;

import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.AxisType;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.component.charts.model.Configuration;
import com.vaadin.flow.component.charts.model.DataSeries;
import com.vaadin.flow.component.charts.model.DataSeriesItem;
import com.vaadin.flow.component.charts.model.XAxis;
import com.vaadin.flow.component.dependency.CssImport;
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.irr.domain.CashflowRow;

import java.time.ZoneOffset;
import java.util.List;

/**
 * A column chart of the schedule along a date axis: investments drop below the
 * zero line, withdrawals rise above it, so the shape of the plan — money out
 * then money back — reads at a glance. Series order fixes the palette index the
 * shadow CSS recolours: 0 = investments, 1 = withdrawals.
 */
@CssImport(value = "./shadow/cashflow-timeline-chart.css", themeFor = "vaadin-chart")
public class CashflowTimelineChart extends Chart {

    public CashflowTimelineChart() {
        super(ChartType.COLUMN);
        addClassName("cashflow-timeline-chart");
        setWidthFull();
        setHeight("340px");

        final Configuration configuration = getConfiguration();
        configuration.setTitle(Translations.get("chart.xirr.timelineTitle"));
        configuration.getChart().setStyledMode(true);
        configuration.getLegend().setEnabled(true);

        final XAxis xAxis = configuration.getxAxis();
        xAxis.setType(AxisType.DATETIME);
        xAxis.setTitle(Translations.get("chart.axis.date"));
    }

    public void update(List<CashflowRow> rows, SupportedCurrency currency) {
        final DataSeries investments = new DataSeries(Translations.get("chart.xirr.series.investments"));
        final DataSeries withdrawals = new DataSeries(Translations.get("chart.xirr.series.withdrawals"));
        for (final CashflowRow row : rows) {
            final DataSeriesItem item = new DataSeriesItem(epochMillis(row), row.amount().doubleValue());
            if (row.amount().signum() < 0) {
                investments.add(item);
            } else {
                withdrawals.add(item);
            }
        }

        final Configuration configuration = getConfiguration();
        configuration.getyAxis().setTitle(currency.name());
        configuration.getyAxis().getLabels().setFormatter(MoneyFormatter.compactAxisFormatterJs(currency));
        configuration.setSeries(investments, withdrawals);
        drawChart(true);
    }

    private static long epochMillis(CashflowRow row) {
        return row.date().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }
}
