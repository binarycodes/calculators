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
import com.vaadin.flow.component.dependency.CssImport;
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.retirement.domain.ProjectionRow;
import io.binarycodes.calculators.retirement.domain.RetirementResult;

import java.math.BigDecimal;

/**
 * Donut chart breaking down the corpus at retirement into the principal
 * the user contributed vs. the interest earned. Legend on the right
 * carries the formatted money amounts.
 */
@CssImport(value = "./shadow/investments-chart.css", themeFor = "vaadin-chart")
public class InvestmentsChart extends Chart {
    private static final String INVESTMENT_CLASSNAME = "investment";
    private static final String INTEREST_CLASSNAME = "interest";

    public InvestmentsChart() {
        super(ChartType.PIE);
        addClassName("investments-chart");
        setWidthFull();
        setHeight("340px");

        final Configuration cfg = getConfiguration();
        cfg.setTitle(Translations.get("chart.retirement.investmentsAtRetirement"));
        cfg.getTitle().setAlign(HorizontalAlign.LEFT);
        cfg.getChart().setStyledMode(true);
        cfg.getLegend().setEnabled(true);
        cfg.getLegend().setLayout(LayoutDirection.VERTICAL);
        cfg.getLegend().setAlign(HorizontalAlign.RIGHT);
        cfg.getLegend().setVerticalAlign(VerticalAlign.MIDDLE);
    }

    /**
     * Principal/interest split of the corpus at retirement.
     *
     * @param principal everything the user contributed by retirement
     * @param interest  accumulated gains (corpus minus principal)
     * @param total     the corpus at retirement
     */
    public record Breakdown(BigDecimal principal, BigDecimal interest, BigDecimal total) {
    }

    /**
     * Splits the corpus at retirement into contributed principal and earned
     * interest. {@code investedAtRetirement} is captured just before the
     * retirement-year contribution lands (per the spec), whereas the
     * {@code startCorpus} snapshot is taken just after it — so the principal
     * inside that corpus is {@code investedAtRetirement} plus the retirement-year
     * contribution, and only the remainder is interest.
     */
    static Breakdown breakdown(RetirementResult result) {
        final BigDecimal invested = result.investedAtRetirement();
        final ProjectionRow retire = result.rows().stream()
                .filter(ProjectionRow::isRetireYear).findFirst().orElse(null);
        if (retire == null) {
            return new Breakdown(invested, BigDecimal.ZERO, invested);
        }
        final BigDecimal total = retire.startCorpus();
        final BigDecimal principal = invested.add(retire.investment());
        final BigDecimal interest = total.subtract(principal).max(BigDecimal.ZERO);
        return new Breakdown(principal, interest, total);
    }

    public void update(RetirementResult result, SupportedCurrency currency) {
        final Breakdown breakdown = breakdown(result);
        final BigDecimal invested = breakdown.principal();
        final BigDecimal interest = breakdown.interest();
        final BigDecimal total = breakdown.total();

        // Slice names embed the formatted amount so they appear next to each
        // legend swatch (Vaadin Charts builds the legend from series item names).
        final var investedSeriesItem = new DataSeriesItem(Translations.get("chart.retirement.invested") + " · " + MoneyFormatter.format(invested, currency), invested.doubleValue());
        investedSeriesItem.setClassName(INVESTMENT_CLASSNAME);

        final var interestSeriesItem = new DataSeriesItem(Translations.get("chart.series.interest") + " · " + MoneyFormatter.format(interest, currency), interest.doubleValue());
        interestSeriesItem.setClassName(INTEREST_CLASSNAME);

        final DataSeries series = new DataSeries(investedSeriesItem, interestSeriesItem);

        final PlotOptionsPie pie = new PlotOptionsPie();
        pie.setInnerSize("65%");
        pie.setShowInLegend(true);
        pie.setCenter("30%", "50%");
        pie.getDataLabels().setEnabled(true);
        pie.getDataLabels().setFormat("<b>{point.percentage:.1f}%</b>");

        final Configuration cfg = getConfiguration();
        cfg.setSubTitle(Translations.get("chart.retirement.total") + " · " + MoneyFormatter.format(total, currency));
        cfg.getSubTitle().setAlign(HorizontalAlign.LEFT);
        cfg.setSeries(series);
        cfg.setPlotOptions(pie);
        drawChart(true);
    }
}
