package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.component.charts.model.Configuration;
import com.vaadin.flow.component.charts.model.ListSeries;
import com.vaadin.flow.component.charts.model.PlotOptionsColumn;
import com.vaadin.flow.component.charts.model.Stacking;
import com.vaadin.flow.component.dependency.CssImport;
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.retirement.domain.ProjectionRow;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;
import io.binarycodes.calculators.retirement.domain.RetirementResult;

import java.math.BigDecimal;

/**
 * Stacked column chart of yearly return on investments — the annual
 * contribution (investment) at the bottom, the interest earned that year
 * stacked on top. Plotted across the full projection window the calculator
 * produced: every year up to life expectancy, or earlier if the corpus is
 * depleted.
 */
@CssImport(value = "./shadow/roi-chart.css", themeFor = "vaadin-chart")
public class ReturnOnInvestmentsChart extends Chart {
    private static final String INVESTMENT_CLASSNAME = "investment";
    private static final String INTEREST_CLASSNAME = "interest";

    public ReturnOnInvestmentsChart() {
        super(ChartType.COLUMN);
        setWidthFull();
        setHeight("340px");

        final Configuration cfg = getConfiguration();
        cfg.setTitle(Translations.get("chart.retirement.returnOnInvestments"));
        cfg.getChart().setStyledMode(true);
        cfg.getxAxis().setTitle(Translations.get("chart.axis.age"));
        cfg.getLegend().setEnabled(true);
    }

    public void update(RetirementInputs inputs, RetirementResult result, SupportedCurrency currency) {
        final int rowCount = result.rows().size();
        final Number[] investmentValues = new Number[rowCount];
        final Number[] interestValues = new Number[rowCount];
        final String[] categories = new String[rowCount];
        final BigDecimal initialCorpus = inputs.getCorpus() == null ? BigDecimal.ZERO : inputs.getCorpus();
        for (int index = 0; index < rowCount; index++) {
            final ProjectionRow row = result.rows().get(index);
            final BigDecimal investmentForYear = index == 0
                    ? row.investment().add(initialCorpus)
                    : row.investment();
            investmentValues[index] = investmentForYear.doubleValue();
            interestValues[index] = row.returns().doubleValue();
            categories[index] = Integer.toString(row.age());
        }

        final PlotOptionsColumn investmentOptions = new PlotOptionsColumn();
        investmentOptions.setStacking(Stacking.NORMAL);
        investmentOptions.setClassName(INVESTMENT_CLASSNAME);

        final PlotOptionsColumn interestOptions = new PlotOptionsColumn();
        interestOptions.setStacking(Stacking.NORMAL);
        interestOptions.setClassName(INTEREST_CLASSNAME);

        final ListSeries investmentSeries = new ListSeries(Translations.get("chart.series.investment"), investmentValues);
        investmentSeries.setPlotOptions(investmentOptions);

        final ListSeries interestSeries = new ListSeries(Translations.get("chart.series.interest"), interestValues);
        interestSeries.setPlotOptions(interestOptions);

        final Configuration cfg = getConfiguration();
        cfg.setSeries(investmentSeries, interestSeries);
        cfg.getxAxis().setCategories(categories);
        cfg.getyAxis().setTitle(currency.name());
        cfg.getyAxis().getLabels().setFormatter(MoneyFormatter.compactAxisFormatterJs(currency));
        cfg.getyAxis().setReversedStacks(false);

        drawChart(true);
    }
}
