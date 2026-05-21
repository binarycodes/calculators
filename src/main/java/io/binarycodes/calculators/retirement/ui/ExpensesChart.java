package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.component.charts.model.Configuration;
import com.vaadin.flow.component.charts.model.ListSeries;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.retirement.domain.RetirementResult;

/** Area-spline chart of annual expenses across the years (inflation curve). */
public class ExpensesChart extends Chart {

    public ExpensesChart() {
        super(ChartType.AREASPLINE);
        setWidthFull();
        setHeight("340px");

        final Configuration cfg = getConfiguration();
        cfg.setTitle("Annual Expenses");
        cfg.getChart().setStyledMode(true);
        cfg.getxAxis().setTitle("Age");
    }

    public void update(RetirementResult result, SupportedCurrency currency) {
        final Number[] ys = new Number[result.rows().size()];
        final String[] cats = new String[result.rows().size()];
        for (int i = 0; i < result.rows().size(); i++) {
            ys[i] = result.rows().get(i).annualExp().doubleValue();
            cats[i] = Integer.toString(result.rows().get(i).age());
        }

        final Configuration cfg = getConfiguration();
        cfg.getyAxis().setTitle(currency.name());
        cfg.setSeries(new ListSeries("Annual Expenses", ys));
        cfg.getxAxis().setCategories(cats);
        drawChart(true);
    }
}
