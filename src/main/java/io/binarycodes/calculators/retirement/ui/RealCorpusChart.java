package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.component.charts.model.Configuration;
import com.vaadin.flow.component.charts.model.ListSeries;
import com.vaadin.flow.component.charts.model.Marker;
import com.vaadin.flow.component.charts.model.PlotOptionsAreaspline;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.retirement.domain.ProjectionRow;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;
import io.binarycodes.calculators.retirement.domain.RetirementResult;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Side-by-side area-spline panes comparing the nominal corpus trajectory
 * with the same trajectory deflated to today's money. Each pane uses its
 * own y-scale so the real-corpus shape is fully visible instead of being
 * crushed against the axis by the much larger nominal numbers.
 */
@CssImport(value = "./shadow/real-corpus-chart.css", themeFor = "vaadin-chart")
public class RealCorpusChart extends SplitLayout {
    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static final String NOMINAL_CLASSNAME = "nominal";
    private static final String REAL_CLASSNAME = "real";

    private final Chart nominalChart = buildPane("Nominal Corpus", NOMINAL_CLASSNAME);
    private final Chart realChart = buildPane("Real Corpus (today's money)", REAL_CLASSNAME);

    public RealCorpusChart() {
        setOrientation(Orientation.HORIZONTAL);
        setWidthFull();
        setHeight("340px");
        setSplitterPosition(50);
        addToPrimary(this.nominalChart);
        addToSecondary(this.realChart);
    }

    public void update(RetirementInputs inputs, RetirementResult result, SupportedCurrency currency) {
        final int rowCount = result.rows().size();
        final Number[] nominalValues = new Number[rowCount];
        final Number[] realValues = new Number[rowCount];
        final String[] categories = new String[rowCount];

        final BigDecimal inflation = pctToFraction(inputs.getInflationPct());
        final int currentAge = inputs.getCurrentAge();

        for (int index = 0; index < rowCount; index++) {
            final ProjectionRow row = result.rows().get(index);
            final BigDecimal nominal = row.endCorpus();
            final BigDecimal discount = pow1plus(inflation, row.age() - currentAge + 1);
            final BigDecimal real = nominal.divide(discount, MC);

            nominalValues[index] = Math.max(nominal.doubleValue(), 0);
            realValues[index] = Math.max(real.doubleValue(), 0);
            categories[index] = Integer.toString(row.age() + 1);
        }

        applySeries(this.nominalChart, "Nominal", nominalValues, categories, currency, NOMINAL_CLASSNAME);
        applySeries(this.realChart, "Real", realValues, categories, currency, REAL_CLASSNAME);
    }

    private static Chart buildPane(String title, String className) {
        final Chart chart = new Chart(ChartType.AREASPLINE);
        chart.setSizeFull();

        final Configuration cfg = chart.getConfiguration();
        cfg.setTitle(title);
        cfg.getChart().setStyledMode(true);
        cfg.getxAxis().setTitle("Age");
        cfg.getLegend().setEnabled(false);
        cfg.getChart().setClassName(className);
        return chart;
    }

    private static void applySeries(Chart chart, String seriesName, Number[] values,
                                    String[] categories, SupportedCurrency currency, String className) {
        final PlotOptionsAreaspline plotOptions = new PlotOptionsAreaspline();
        plotOptions.setMarker(new Marker(false));
        plotOptions.setClassName(className);

        final ListSeries series = new ListSeries(seriesName, values);
        series.setPlotOptions(plotOptions);

        final Configuration cfg = chart.getConfiguration();
        cfg.setSeries(series);
        cfg.getxAxis().setCategories(categories);
        cfg.getyAxis().setTitle(currency.name());
        cfg.getyAxis().getLabels().setFormatter(MoneyFormatter.compactAxisFormatterJs(currency));

        chart.drawChart(true);
    }

    private static BigDecimal pctToFraction(BigDecimal pct) {
        return pct == null ? BigDecimal.ZERO : pct.divide(HUNDRED, MC);
    }

    private static BigDecimal pow1plus(BigDecimal rate, int years) {
        if (years <= 0) {
            return BigDecimal.ONE;
        }
        final BigDecimal base = BigDecimal.ONE.add(rate, MC);
        BigDecimal acc = BigDecimal.ONE;
        for (int step = 0; step < years; step++) {
            acc = acc.multiply(base, MC);
        }
        return acc;
    }
}
