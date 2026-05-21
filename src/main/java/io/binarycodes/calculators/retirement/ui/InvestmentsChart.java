package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.component.charts.model.Configuration;
import com.vaadin.flow.component.charts.model.DataSeries;
import com.vaadin.flow.component.charts.model.DataSeriesItem;
import com.vaadin.flow.component.charts.model.HorizontalAlign;
import com.vaadin.flow.component.charts.model.LayoutDirection;
import com.vaadin.flow.component.charts.model.PlotOptionsPie;
import com.vaadin.flow.component.charts.model.VerticalAlign;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.retirement.domain.ProjectionRow;
import io.binarycodes.calculators.retirement.domain.RetirementResult;

import java.math.BigDecimal;

/** Donut chart breaking down the corpus at retirement into the principal
 *  the user contributed vs. the interest earned. Legend on the right
 *  carries the formatted money amounts. */
public class InvestmentsChart extends Chart {

    public InvestmentsChart() {
        super(ChartType.PIE);
        setWidthFull();
        setHeight("340px");

        final Configuration cfg = getConfiguration();
        cfg.setTitle("Investments at Retirement");
        cfg.getTitle().setAlign(HorizontalAlign.LEFT);
        cfg.getChart().setStyledMode(true);
        cfg.getLegend().setEnabled(true);
        cfg.getLegend().setLayout(LayoutDirection.VERTICAL);
        cfg.getLegend().setAlign(HorizontalAlign.RIGHT);
        cfg.getLegend().setVerticalAlign(VerticalAlign.MIDDLE);
    }

    public void update(RetirementResult result, SupportedCurrency currency) {
        final BigDecimal invested = result.investedAtRetirement();
        final ProjectionRow retire = result.rows().stream()
                .filter(ProjectionRow::isRetireYear).findFirst().orElse(null);
        final BigDecimal total = retire == null ? invested : retire.startCorpus();
        final BigDecimal interest = total.subtract(invested).max(BigDecimal.ZERO);

        // Slice names embed the formatted amount so they appear next to each
        // legend swatch (Vaadin Charts builds the legend from series item names).
        final DataSeries series = new DataSeries();
        series.add(new DataSeriesItem(
                "Invested · " + MoneyFormatter.format(invested, currency), invested.doubleValue()));
        series.add(new DataSeriesItem(
                "Interest · " + MoneyFormatter.format(interest, currency), interest.doubleValue()));

        final PlotOptionsPie pie = new PlotOptionsPie();
        pie.setInnerSize("65%");
        pie.setShowInLegend(true);
        pie.setCenter("30%", "50%");
        pie.getDataLabels().setEnabled(true);
        pie.getDataLabels().setFormat("<b>{point.percentage:.1f}%</b>");

        final Configuration cfg = getConfiguration();
        cfg.setSubTitle("Total · " + MoneyFormatter.format(total, currency));
        cfg.getSubTitle().setAlign(HorizontalAlign.LEFT);
        cfg.setSeries(series);
        cfg.setPlotOptions(pie);
        drawChart(true);
    }
}
