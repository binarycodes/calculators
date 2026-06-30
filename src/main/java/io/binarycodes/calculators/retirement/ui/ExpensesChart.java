package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.component.charts.model.Configuration;
import com.vaadin.flow.component.charts.model.ListSeries;
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import com.vaadin.flow.component.charts.model.Marker;
import com.vaadin.flow.component.charts.model.PlotOptionsAreaspline;
import com.vaadin.flow.component.dependency.CssImport;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.retirement.domain.RetirementResult;

/**
 * Area-spline chart of annual expenses across the years (inflation curve).
 */
@CssImport(value = "./shadow/expenses-chart.css", themeFor = "vaadin-chart")
public class ExpensesChart extends Chart {

    public ExpensesChart() {
        super(ChartType.AREASPLINE);
        addClassName("expenses-chart");
        setWidthFull();
        setHeight("340px");

        final Configuration cfg = getConfiguration();
        cfg.setTitle(Translations.get("chart.retirement.annualExpenses"));
        cfg.getChart().setStyledMode(true);
        cfg.getxAxis().setTitle(Translations.get("chart.axis.age"));
    }

    public void update(RetirementResult result, SupportedCurrency currency) {
        final Number[] ys = new Number[result.rows().size()];
        final String[] categories = new String[result.rows().size()];
        for (int i = 0; i < result.rows().size(); i++) {
            ys[i] = result.rows().get(i).annualExp().doubleValue();
            categories[i] = Integer.toString(RetirementChartAxis.yearEndAge(result.rows().get(i).age()));
        }

        final var plotOptions = new PlotOptionsAreaspline();
        plotOptions.setMarker(new Marker(false));

        final var config = getConfiguration();
        config.getyAxis().setTitle(currency.name());
        config.getyAxis().getLabels().setFormatter(MoneyFormatter.compactAxisFormatterJs(currency));
        config.setSeries(new ListSeries(Translations.get("chart.retirement.annualExpenses"), ys));
        config.getxAxis().setCategories(categories);
        config.setPlotOptions(plotOptions);

        drawChart(true);
    }
}
