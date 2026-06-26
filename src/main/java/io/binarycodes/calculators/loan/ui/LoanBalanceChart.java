package io.binarycodes.calculators.loan.ui;

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
import io.binarycodes.calculators.loan.domain.LoanResult;
import io.binarycodes.calculators.loan.domain.LoanYear;

import java.math.BigDecimal;

/**
 * Outstanding-balance trajectory over the years. The baseline curve is always
 * shown; when prepayments are active a second curve shows how much faster the
 * balance falls, making the time saved visible at a glance.
 */
@CssImport(value = "./shadow/loan-balance-chart.css", themeFor = "vaadin-chart")
public class LoanBalanceChart extends Chart {

    private static final String BASELINE_CLASSNAME = "baseline";
    private static final String PREPAY_CLASSNAME = "with-prepay";

    public LoanBalanceChart() {
        super(ChartType.LINE);
        addClassName("loan-balance-chart");
        setWidthFull();
        setHeight("340px");

        final Configuration configuration = getConfiguration();
        // Title omitted — the enclosing tab is labelled "Outstanding Balance".
        configuration.setTitle("");
        configuration.getChart().setStyledMode(true);
        configuration.getLegend().setEnabled(true);
        final XAxis xAxis = configuration.getxAxis();
        xAxis.setTitle(Translations.get("chart.axis.year"));
        xAxis.setType(AxisType.LINEAR);
        xAxis.setAllowDecimals(false);
    }

    public void update(LoanResult result, BigDecimal principal, SupportedCurrency currency) {
        final Configuration configuration = getConfiguration();
        configuration.getyAxis().setTitle(currency.name());
        configuration.getyAxis().getLabels().setFormatter(MoneyFormatter.compactAxisFormatterJs(currency));

        if (result.hasPrepayments()) {
            configuration.setSeries(
                    balanceSeries(Translations.get("loan.series.withoutPrepayment"), BASELINE_CLASSNAME, result.baselineRows(), principal),
                    balanceSeries(Translations.get("loan.series.withPrepayment"), PREPAY_CLASSNAME, result.rows(), principal));
        } else {
            configuration.setSeries(
                    balanceSeries(Translations.get("loan.series.balance"), PREPAY_CLASSNAME, result.rows(), principal));
        }
        drawChart(true);
    }

    private static DataSeries balanceSeries(String name, String className, java.util.List<LoanYear> rows,
                                            BigDecimal principal) {
        final DataSeries series = new DataSeries(name);
        final PlotOptionsLine options = new PlotOptionsLine();
        options.setMarker(new Marker(false));
        options.setClassName(className);
        series.setPlotOptions(options);

        if (!rows.isEmpty()) {
            // Anchor the curve at the full principal the year before the first row.
            series.add(new DataSeriesItem(rows.get(0).year() - 1, principal.doubleValue()));
        }
        for (final LoanYear row : rows) {
            series.add(new DataSeriesItem(row.year(), Math.max(row.endBalance().doubleValue(), 0)));
        }
        return series;
    }
}
