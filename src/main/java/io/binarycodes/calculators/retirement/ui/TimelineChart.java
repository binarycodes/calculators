package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.component.charts.model.Configuration;
import com.vaadin.flow.component.charts.model.DataLabels;
import com.vaadin.flow.component.charts.model.DataSeries;
import com.vaadin.flow.component.charts.model.DataSeriesItemTimeline;
import com.vaadin.flow.component.charts.model.PlotOptionsTimeline;
import com.vaadin.flow.component.charts.model.Tooltip;
import com.vaadin.flow.component.charts.model.XAxis;
import com.vaadin.flow.component.dependency.CssImport;
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.retirement.domain.TimelineEvent;
import io.binarycodes.calculators.retirement.domain.TimelineEventType;
import io.binarycodes.calculators.retirement.domain.TimelineYear;

import java.util.List;

/**
 * A {@link ChartType#TIMELINE} chart that tells the plan's story along the age
 * axis: current state, retirement, lump-sum benefits, one-off and recurring
 * cashflows, wealth milestones, the year drawdown begins, and depletion. Events
 * sharing a year are clubbed into one marker (inline label = the event count;
 * the hover tooltip lists them all). Retirement, drawdown and depletion years
 * carry distinct marker colours.
 */
@CssImport(value = "./shadow/timeline-chart.css", themeFor = "vaadin-chart")
public class TimelineChart extends Chart {

    private static final String RETIREMENT_YEAR_CLASSNAME = "retirement-year";
    private static final String DRAWDOWN_YEAR_CLASSNAME = "drawdown-year";
    private static final String DEPLETION_YEAR_CLASSNAME = "depletion-year";
    private static final String EVENT_YEAR_CLASSNAME = "event-year";

    public TimelineChart() {
        super(ChartType.TIMELINE);
        addClassName("timeline-chart");
        setWidthFull();
        setHeight("340px");

        final Configuration configuration = getConfiguration();
        configuration.setTitle(Translations.get("chart.retirement.timelineTitle"));
        configuration.getChart().setStyledMode(true);
        configuration.getLegend().setEnabled(false);

        final XAxis xAxis = configuration.getxAxis();
        xAxis.setTitle(Translations.get("chart.axis.age"));
        xAxis.setAllowDecimals(false);
        configuration.getyAxis().setVisible(false);

        final Tooltip tooltip = configuration.getTooltip();
        tooltip.setUseHTML(true);
        tooltip.setHeaderFormat("");
        tooltip.setPointFormat("{point.description}");
    }

    public void update(List<TimelineYear> years, SupportedCurrency currency) {
        final DataSeries series = new DataSeries();

        final PlotOptionsTimeline plotOptions = new PlotOptionsTimeline();
        // Timeline defaults to one palette colour per point; we colour by event
        // type via per-point CSS classes instead, so turn the rainbow off.
        plotOptions.setColorByPoint(false);
        final DataLabels dataLabels = new DataLabels();
        dataLabels.setEnabled(true);
        // Inline marker text is just the clubbed event count; the detail lives in
        // the tooltip so the timeline stays readable when years sit close together.
        dataLabels.setFormat("{point.name}");
        plotOptions.setDataLabels(dataLabels);
        series.setPlotOptions(plotOptions);

        for (final TimelineYear year : years) {
            final String count = countLabel(year.size());
            final DataSeriesItemTimeline item = new DataSeriesItemTimeline(
                    year.age(), count, count, tooltipHtml(year, currency));
            item.setClassName(markerClass(year.dominantType()));
            series.add(item);
        }

        getConfiguration().setSeries(series);
        drawChart(true);
    }

    private static String countLabel(int size) {
        return size == 1
                ? Translations.get("chart.retirement.timeline.eventCount.one", size)
                : Translations.get("chart.retirement.timeline.eventCount.other", size);
    }

    private static String tooltipHtml(TimelineYear year, SupportedCurrency currency) {
        final StringBuilder html = new StringBuilder()
                .append("<b>")
                .append(escape(Translations.get("chart.retirement.timeline.yearHeader",
                        year.age(), String.valueOf(year.year()))))
                .append("</b><ul style=\"margin:4px 0 0;padding-left:16px\">");
        for (final TimelineEvent event : year.events()) {
            html.append("<li>").append(eventLine(event, currency)).append("</li>");
        }
        return html.append("</ul>").toString();
    }

    private static String eventLine(TimelineEvent event, SupportedCurrency currency) {
        if (event.type() == TimelineEventType.MILESTONE) {
            return escape(Translations.get("chart.retirement.timeline.milestone",
                    MoneyFormatter.formatShort(event.amount(), currency)));
        }
        final StringBuilder line = new StringBuilder(escape(typeLabel(event.type())));
        if (event.amount() != null) {
            line.append(" (").append(escape(MoneyFormatter.formatShort(event.amount(), currency))).append(')');
        }
        if (event.detail() != null && !event.detail().isBlank()) {
            line.append(" — ").append(escape(event.detail()));
        }
        return line.toString();
    }

    private static String typeLabel(TimelineEventType type) {
        final String key = switch (type) {
            case CURRENT_STATE -> "chart.retirement.timeline.currentState";
            case RETIREMENT -> "chart.retirement.timeline.retirement";
            case RETIREMENT_BENEFIT -> "chart.retirement.timeline.retirementBenefit";
            case FUTURE_INCOME -> "chart.retirement.timeline.futureIncome";
            case FUTURE_EXPENSE -> "chart.retirement.timeline.futureExpense";
            case RECURRING_INCOME_START -> "chart.retirement.timeline.recurringIncomeStart";
            case RECURRING_INCOME_STOP -> "chart.retirement.timeline.recurringIncomeStop";
            case RECURRING_EXPENSE_START -> "chart.retirement.timeline.recurringExpenseStart";
            case RECURRING_EXPENSE_STOP -> "chart.retirement.timeline.recurringExpenseStop";
            case DRAWDOWN_BEGINS -> "chart.retirement.timeline.drawdownBegins";
            case DEPLETION -> "chart.retirement.timeline.depletion";
            case MILESTONE -> "chart.retirement.timeline.milestone";
        };
        return Translations.get(key);
    }

    private static String markerClass(TimelineEventType dominantType) {
        return switch (dominantType) {
            case DEPLETION -> DEPLETION_YEAR_CLASSNAME;
            case DRAWDOWN_BEGINS -> DRAWDOWN_YEAR_CLASSNAME;
            case RETIREMENT -> RETIREMENT_YEAR_CLASSNAME;
            default -> EVENT_YEAR_CLASSNAME;
        };
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
