package io.binarycodes.calculators.debt.ui;

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
import io.binarycodes.calculators.debt.domain.DebtPlanResult;
import io.binarycodes.calculators.debt.domain.DebtPlanYear;
import io.binarycodes.calculators.debt.domain.DebtScheduleResult;

import java.util.List;

/**
 * Total-outstanding-balance trajectory: Avalanche, Snowball, and the
 * minimums-only baseline as three lines over the years. The two strategy lines
 * reach zero sooner than the baseline — the visible gap is the time the extra
 * payment and ordering buy back.
 */
@CssImport(value = "./shadow/debt-chart.css", themeFor = "vaadin-chart")
public class DebtComparisonChart extends Chart {

    private static final String AVALANCHE_CLASSNAME = "debt-avalanche";
    private static final String SNOWBALL_CLASSNAME = "debt-snowball";
    private static final String BASELINE_CLASSNAME = "debt-baseline";

    public DebtComparisonChart() {
        super(ChartType.LINE);
        setWidthFull();
        setHeight("340px");
        addClassName("debt-chart");

        final Configuration configuration = getConfiguration();
        configuration.setTitle(Translations.get("chart.debt.title"));
        configuration.getChart().setStyledMode(true);
        configuration.getLegend().setEnabled(true);
        final XAxis xAxis = configuration.getxAxis();
        xAxis.setTitle(Translations.get("chart.axis.year"));
        xAxis.setType(AxisType.LINEAR);
        xAxis.setAllowDecimals(false);
    }

    public void update(DebtPlanResult result, SupportedCurrency currency) {
        final Configuration configuration = getConfiguration();
        configuration.getyAxis().setTitle(currency.name());
        configuration.getyAxis().getLabels().setFormatter(MoneyFormatter.compactAxisFormatterJs(currency));

        configuration.setSeries(
                series(Translations.get("chart.debt.seriesAvalanche"), AVALANCHE_CLASSNAME, result.avalanche()),
                series(Translations.get("chart.debt.seriesSnowball"), SNOWBALL_CLASSNAME, result.snowball()),
                series(Translations.get("chart.debt.seriesBaseline"), BASELINE_CLASSNAME, result.baseline()));
        drawChart(true);
    }

    private static DataSeries series(String name, String className, DebtScheduleResult schedule) {
        final DataSeries series = new DataSeries(name);
        final PlotOptionsLine options = new PlotOptionsLine();
        options.setMarker(new Marker(false));
        options.setClassName(className);
        series.setPlotOptions(options);

        final List<DebtPlanYear> years = schedule.years();
        for (final DebtPlanYear year : years) {
            series.add(new DataSeriesItem(year.year(), year.totalBalance().doubleValue()));
        }
        return series;
    }
}
