package io.binarycodes.calculators.buyrent.ui;

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
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.buyrent.domain.BuyRentResult;
import io.binarycodes.calculators.buyrent.domain.BuyRentYear;

/**
 * Net-worth trajectory chart. The "Buy" series shows equity (home value net of
 * sell costs and outstanding mortgage); the "Rent" series shows the investment
 * portfolio. Where they cross is the break-even year.
 */
@CssImport(value = "./shadow/buyrent-chart.css", themeFor = "vaadin-chart")
public class BuyRentComparisonChart extends Chart {

    private static final String BUY_CLASSNAME = "buyrent-buy";
    private static final String RENT_CLASSNAME = "buyrent-rent";

    public BuyRentComparisonChart() {
        super(ChartType.LINE);
        setWidthFull();
        setHeight("340px");
        addClassName("buyrent-chart");

        final Configuration configuration = getConfiguration();
        configuration.setTitle("");
        configuration.getChart().setStyledMode(true);
        configuration.getLegend().setEnabled(true);
        final XAxis xAxis = configuration.getxAxis();
        xAxis.setTitle("Year");
        xAxis.setType(AxisType.LINEAR);
        xAxis.setAllowDecimals(false);
    }

    public void update(BuyRentResult result, SupportedCurrency currency) {
        final Configuration configuration = getConfiguration();
        configuration.getyAxis().setTitle(currency.name());
        configuration.getyAxis().getLabels().setFormatter(MoneyFormatter.compactAxisFormatterJs(currency));

        final DataSeries buySeries = new DataSeries("Buy (equity)");
        final PlotOptionsLine buyOptions = new PlotOptionsLine();
        buyOptions.setMarker(new Marker(false));
        buyOptions.setClassName(BUY_CLASSNAME);
        buySeries.setPlotOptions(buyOptions);

        final DataSeries rentSeries = new DataSeries("Rent (portfolio)");
        final PlotOptionsLine rentOptions = new PlotOptionsLine();
        rentOptions.setMarker(new Marker(false));
        rentOptions.setClassName(RENT_CLASSNAME);
        rentSeries.setPlotOptions(rentOptions);

        for (final BuyRentYear row : result.rows()) {
            buySeries.add(new DataSeriesItem(row.year(), row.equityAfterTax().doubleValue()));
            rentSeries.add(new DataSeriesItem(row.year(), row.rentPortfolioAfterTax().doubleValue()));
        }

        configuration.setSeries(buySeries, rentSeries);
        drawChart(true);
    }
}
