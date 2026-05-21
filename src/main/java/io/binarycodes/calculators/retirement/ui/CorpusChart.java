package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.component.charts.model.Configuration;
import com.vaadin.flow.component.charts.model.DashStyle;
import com.vaadin.flow.component.charts.model.DataSeries;
import com.vaadin.flow.component.charts.model.DataSeriesItem;
import com.vaadin.flow.component.charts.model.Marker;
import com.vaadin.flow.component.charts.model.PlotLine;
import com.vaadin.flow.component.charts.model.PlotOptionsAreaspline;
import com.vaadin.flow.component.charts.model.XAxis;
import com.vaadin.flow.component.charts.model.style.SolidColor;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.retirement.domain.ProjectionRow;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;
import io.binarycodes.calculators.retirement.domain.RetirementResult;

/** Area-spline chart of corpus trajectory across the years, with vertical
 *  markers for the retirement year and (if reached) the depletion year. */
public class CorpusChart extends Chart {

    private static final String RETIREMENT_MARKER_COLOR = "#14b8a6";
    private static final String DEPLETION_MARKER_COLOR  = "#ef4444";

    public CorpusChart() {
        super(ChartType.AREASPLINE);
        setWidthFull();
        setHeight("340px");

        final Configuration cfg = getConfiguration();
        cfg.setTitle("Corpus Trajectory");
        cfg.getChart().setStyledMode(true);
        cfg.getxAxis().setTitle("Age");
    }

    public void update(RetirementInputs inputs, RetirementResult result, SupportedCurrency currency) {
        final DataSeries series = new DataSeries("Corpus");
        series.add(new DataSeriesItem(inputs.getCurrentAge(), inputs.getCorpus().doubleValue()));
        for (final ProjectionRow row : result.rows()) {
            series.add(new DataSeriesItem(row.age() + 1, Math.max(row.endCorpus().doubleValue(), 0)));
        }

        final PlotOptionsAreaspline plot = new PlotOptionsAreaspline();
        plot.setMarker(new Marker(false));

        final Configuration cfg = getConfiguration();
        cfg.getyAxis().setTitle(currency.name());
        cfg.setSeries(series);
        cfg.setPlotOptions(plot);

        final XAxis x = cfg.getxAxis();
        x.setPlotLines();
        x.addPlotLine(plotLine(inputs.getRetireAge(), RETIREMENT_MARKER_COLOR));
        result.corpusDepletedAt()
                .ifPresent(age -> x.addPlotLine(plotLine(age, DEPLETION_MARKER_COLOR)));

        drawChart(true);
    }

    private static PlotLine plotLine(int age, String hexColor) {
        final PlotLine line = new PlotLine();
        line.setValue(age);
        line.setColor(new SolidColor(hexColor));
        line.setDashStyle(DashStyle.SHORTDASH);
        line.setWidth(2);
        return line;
    }
}
