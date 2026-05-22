package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.component.charts.model.DataSeries;
import com.vaadin.flow.component.charts.model.DataSeriesItem;
import com.vaadin.flow.component.charts.model.Marker;
import com.vaadin.flow.component.charts.model.PlotLine;
import com.vaadin.flow.component.charts.model.PlotOptionsAreaspline;
import com.vaadin.flow.component.charts.model.ZoneAxis;
import com.vaadin.flow.component.charts.model.Zones;
import com.vaadin.flow.component.dependency.CssImport;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;
import io.binarycodes.calculators.retirement.domain.RetirementResult;

/**
 * Area-spline chart of corpus trajectory across the years, with vertical
 * markers for the retirement year and (if reached) the depletion year.
 */
@CssImport(value = "./shadow/corpus-chart.css", themeFor = "vaadin-chart")
public class CorpusChart extends Chart {

    private static final String RETIREMENT_MARKER_CLASSNAME = "retirement_point";
    private static final String DEPLETION_MARKER_CLASSNAME = "depletion_point";

    private static final String PRE_RETIREMENT_ZONE = "pre-retirement-zone";
    private static final String RETIREMENT_ZONE = "retirement-zone";
    private static final String POST_CORPUS_DEPLETION_ZONE = "post-corpus-depletion-zone";

    public CorpusChart() {
        super(ChartType.AREASPLINE);
        setWidthFull();
        setHeight("340px");

        final var cfg = getConfiguration();
        cfg.setTitle("Corpus Trajectory");
        cfg.getChart().setStyledMode(true);
        cfg.getxAxis().setTitle("Age");
    }

    public void update(RetirementInputs inputs, RetirementResult result, SupportedCurrency currency) {
        final var series = new DataSeries("Corpus");
        series.add(new DataSeriesItem(inputs.getCurrentAge(), inputs.getCorpus().doubleValue()));

        for (final var row : result.rows()) {
            series.add(new DataSeriesItem(row.age() + 1, Math.max(row.endCorpus().doubleValue(), 0)));
        }

        final var plotOptions = new PlotOptionsAreaspline();
        plotOptions.setMarker(new Marker(false));
        plotOptions.setZoneAxis(ZoneAxis.X);

        final var config = getConfiguration();
        config.getyAxis().setTitle(currency.name());
        config.getyAxis().getLabels().setFormatter(MoneyFormatter.compactAxisFormatterJs(currency));
        config.setSeries(series);
        config.setPlotOptions(plotOptions);

        final var x = config.getxAxis();
        x.setPlotLines();
        x.addPlotLine(plotLine(inputs.getRetireAge(), RETIREMENT_MARKER_CLASSNAME));

        final var preRetirementZone = new Zones();
        preRetirementZone.setValue(inputs.getRetireAge());
        preRetirementZone.setClassName(PRE_RETIREMENT_ZONE);
        plotOptions.addZone(preRetirementZone);

        final var retirementZone = new Zones();
        retirementZone.setClassName(RETIREMENT_ZONE);
        plotOptions.addZone(retirementZone);

        result.corpusDepletedAt()
                .ifPresent(age -> {
                    x.addPlotLine(plotLine(age, DEPLETION_MARKER_CLASSNAME));

                    retirementZone.setValue(age);

                    final var postCorpusDepletionZone = new Zones();
                    postCorpusDepletionZone.setClassName(POST_CORPUS_DEPLETION_ZONE);

                    plotOptions.addZone(postCorpusDepletionZone);
                });

        drawChart(true);
    }

    private static PlotLine plotLine(int age, String className) {
        final PlotLine line = new PlotLine();
        line.setValue(age);
        line.setClassName(className);
        return line;
    }
}
