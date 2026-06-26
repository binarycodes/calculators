package io.binarycodes.calculators.loan.ui;

import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.AxisType;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.component.charts.model.Configuration;
import com.vaadin.flow.component.charts.model.DataSeries;
import com.vaadin.flow.component.charts.model.DataSeriesItem;
import com.vaadin.flow.component.charts.model.PlotOptionsColumn;
import com.vaadin.flow.component.charts.model.Stacking;
import com.vaadin.flow.component.charts.model.XAxis;
import com.vaadin.flow.component.dependency.CssImport;
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.loan.domain.LoanResult;
import io.binarycodes.calculators.loan.domain.LoanYear;

/**
 * Stacked column chart of where each year's outgo goes — principal at the
 * bottom, interest stacked on top, and any prepayment above that. Early years
 * are mostly the interest wedge; it shrinks as the balance falls, which is the
 * point of the view. Mirrors the default grid scenario (reduce tenure).
 */
@CssImport(value = "./shadow/loan-payment-split-chart.css", themeFor = "vaadin-chart")
public class LoanPaymentSplitChart extends Chart {

    public LoanPaymentSplitChart() {
        super(ChartType.COLUMN);
        // Scopes the palette override in loan-payment-split-chart.css to this chart.
        addClassName("loan-payment-split");
        setWidthFull();
        setHeight("340px");

        final Configuration configuration = getConfiguration();
        configuration.setTitle(Translations.get("loan.tab.principalVsInterest"));
        configuration.getChart().setStyledMode(true);
        configuration.getLegend().setEnabled(true);
        final XAxis xAxis = configuration.getxAxis();
        xAxis.setTitle(Translations.get("chart.axis.year"));
        xAxis.setType(AxisType.LINEAR);
        xAxis.setAllowDecimals(false);
    }

    public void update(LoanResult result, SupportedCurrency currency) {
        // Series order fixes the palette mapping the CSS recolours:
        // 0 = principal, 1 = interest, 2 = prepayment.
        final DataSeries principalSeries = stackedSeries(Translations.get("chart.series.principal"));
        final DataSeries interestSeries = stackedSeries(Translations.get("chart.series.interest"));
        final DataSeries prepaySeries = stackedSeries(Translations.get("loan.series.prepayment"));
        for (final LoanYear row : result.rows()) {
            principalSeries.add(new DataSeriesItem(row.year(), row.principalPaid().doubleValue()));
            interestSeries.add(new DataSeriesItem(row.year(), row.interestPaid().doubleValue()));
            prepaySeries.add(new DataSeriesItem(row.year(), row.prepayment().doubleValue()));
        }

        final Configuration configuration = getConfiguration();
        configuration.getyAxis().setReversedStacks(false);
        configuration.getyAxis().setTitle(currency.name());
        configuration.getyAxis().getLabels().setFormatter(MoneyFormatter.compactAxisFormatterJs(currency));
        if (result.hasPrepayments()) {
            configuration.setSeries(principalSeries, interestSeries, prepaySeries);
        } else {
            configuration.setSeries(principalSeries, interestSeries);
        }

        drawChart(true);
    }

    private static DataSeries stackedSeries(String name) {
        final DataSeries series = new DataSeries(name);
        final PlotOptionsColumn options = new PlotOptionsColumn();
        options.setStacking(Stacking.NORMAL);
        series.setPlotOptions(options);
        return series;
    }
}
