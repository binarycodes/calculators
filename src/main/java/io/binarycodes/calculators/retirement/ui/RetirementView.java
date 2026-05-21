package io.binarycodes.calculators.retirement.ui;


import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.component.charts.model.Configuration;
import com.vaadin.flow.component.charts.model.DashStyle;
import com.vaadin.flow.component.charts.model.DataLabels;
import com.vaadin.flow.component.charts.model.DataSeries;
import com.vaadin.flow.component.charts.model.DataSeriesItem;
import com.vaadin.flow.component.charts.model.ListSeries;
import com.vaadin.flow.component.charts.model.Marker;
import com.vaadin.flow.component.charts.model.PlotLine;
import com.vaadin.flow.component.charts.model.PlotOptionsAreaspline;
import com.vaadin.flow.component.charts.model.PlotOptionsPie;
import com.vaadin.flow.component.charts.model.XAxis;
import com.vaadin.flow.component.charts.model.style.SolidColor;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import io.binarycodes.calculators.base.common.Status;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.retirement.domain.ProjectionRow;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;
import io.binarycodes.calculators.retirement.domain.RetirementResult;
import io.binarycodes.calculators.retirement.service.DefaultsProvider;
import io.binarycodes.calculators.retirement.service.RetirementCalculator;
import io.binarycodes.calculators.retirement.service.RetirementInputsStore;

import java.math.BigDecimal;

/**
 * The retirement calculator screen. Composes the form, summary cards, charts,
 * and projection grid; owns no input fields directly — those live in
 * {@link RetirementCalculatorForm}.
 */
@Route("retirement")
@RouteAlias("")
@Menu(title = "Retirement", icon = "vaadin:piggy-bank", order = 1)
@PageTitle("Retirement Calculator")
public class RetirementView extends VerticalLayout {

    private final UserPreferences prefs;
    private final DefaultsProvider defaults;
    private final RetirementInputsStore store;

    private final RetirementCalculatorForm form;

    private final SummaryCard corpusAtRetirement = new SummaryCard("Corpus at Retirement");
    private final SummaryCard expensesAtRetirement = new SummaryCard("Annual Expenses at Retirement");
    private final SummaryCard lastsUntil = new SummaryCard("Corpus Lasts Until");
    private final SummaryCard finalCorpus = new SummaryCard("Final Corpus");

    private final Chart corpusChart = new Chart(ChartType.AREASPLINE);
    private final Chart expensesChart = new Chart(ChartType.AREASPLINE);
    private final Chart investmentsChart = new Chart(ChartType.PIE);

    private final Grid<ProjectionRow> grid = new Grid<>(ProjectionRow.class, false);

    public RetirementView(UserPreferences prefs, DefaultsProvider defaults, RetirementInputsStore store) {
        this.prefs = prefs;
        this.defaults = defaults;
        this.store = store;

        addClassName("retirement-view");
        setWidthFull();
        setPadding(true);
        setSpacing(true);

        this.form = new RetirementCalculatorForm(prefs);
        this.form.addInputChangeListener(this::onInputChanged);

        add(new H2("Retirement Calculator"));
        add(this.form);
        add(buildActions());
        add(buildSummary());
        add(buildChartsBlock());
        add(buildGridBlock());

        // React to currency switches: re-pull inputs for the new currency,
        // re-format helper text & money values, recalculate.
        prefs.addChangeListener(p -> onCurrencyOrPrefsChange());
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        // Load persisted prefs + per-currency inputs from localStorage.
        this.prefs.loadFromBrowser(() ->
                this.store.load(map -> {
                    applyForCurrency(this.prefs.currency());
                    recalculate();
                })
        );
    }

    // ---- actions --------------------------------------------------------

    private HorizontalLayout buildActions() {
        final Button calc = new Button("Calculate", e -> recalculate());
        calc.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        final Button reset = new Button("Reset", e -> resetToDefaults());
        final HorizontalLayout row = new HorizontalLayout(calc, reset);
        row.addClassName("action-row");
        row.setSpacing(true);
        return row;
    }

    private HorizontalLayout buildSummary() {
        final HorizontalLayout row = new HorizontalLayout(
                this.corpusAtRetirement, this.expensesAtRetirement, this.lastsUntil, this.finalCorpus);
        row.addClassName("summary-row");
        row.setWidthFull();
        row.setFlexGrow(1, this.corpusAtRetirement, this.expensesAtRetirement, this.lastsUntil, this.finalCorpus);
        row.setSpacing(true);
        return row;
    }

    private VerticalLayout buildChartsBlock() {
        final Tabs tabs = new Tabs(new Tab("Corpus"), new Tab("Annual Expenses"), new Tab("Investments"));
        final VerticalLayout host = new VerticalLayout();
        host.setPadding(false);
        host.setSpacing(false);
        host.setSizeFull();
        host.add(this.corpusChart);

        this.corpusChart.setWidthFull();
        this.expensesChart.setWidthFull();
        this.investmentsChart.setWidthFull();

        this.corpusChart.setHeight("340px");
        this.expensesChart.setHeight("340px");
        this.investmentsChart.setHeight("340px");

        tabs.addSelectedChangeListener(e -> {
            host.removeAll();
            switch (e.getSelectedTab().getLabel()) {
                case "Corpus" -> host.add(this.corpusChart);
                case "Annual Expenses" -> host.add(this.expensesChart);
                case "Investments" -> host.add(this.investmentsChart);
            }
        });

        final VerticalLayout block = new VerticalLayout(tabs, host);
        block.addClassName("chart-card");
        block.setPadding(false);
        block.setSpacing(false);
        block.setWidthFull();
        return block;
    }

    private VerticalLayout buildGridBlock() {
        this.grid.addColumn(ProjectionRow::year).setHeader("Year");
        this.grid.addColumn(ProjectionRow::age).setHeader("Age");
        this.grid.addComponentColumn(r -> phaseBadge(r.isPost())).setHeader("Phase");
        this.grid.addColumn(r -> moneyOrDash(r.annualExp(), this.prefs.currency())).setHeader("Annual Expenses").setTextAlign(ColumnTextAlign.END);
        this.grid.addColumn(r -> moneyOrDash(r.startCorpus(), this.prefs.currency())).setHeader("Corpus (Start)").setTextAlign(ColumnTextAlign.END);
        this.grid.addColumn(r -> moneyOrDash(r.returns(), this.prefs.currency())).setHeader("Returns").setTextAlign(ColumnTextAlign.END);
        this.grid.addColumn(r -> moneyOrDash(r.investment(), this.prefs.currency())).setHeader("Investment").setTextAlign(ColumnTextAlign.END);
        this.grid.addColumn(r -> moneyOrDash(r.withdrawal(), this.prefs.currency())).setHeader("Withdrawal").setTextAlign(ColumnTextAlign.END);
        this.grid.addColumn(r -> moneyOrDash(r.endCorpus(), this.prefs.currency())).setHeader("Corpus (End)").setTextAlign(ColumnTextAlign.END)
                .setPartNameGenerator(r -> {
                    if (!r.isPost()) {
                        return null;
                    }
                    if (r.endCorpus().signum() <= 0) {
                        return "corpus-end-depleted";
                    }
                    if (r.endCorpus().compareTo(r.annualExp().multiply(BigDecimal.TEN)) < 0) {
                        return "corpus-end-low";
                    }
                    return "corpus-end-healthy";
                });

        this.grid.setPartNameGenerator(r -> {
            if (r.depleted()) {
                return "depleted-row";
            }
            if (r.isRetireYear()) {
                return "retirement-row";
            }
            if (r.isPost() && r.endCorpus().compareTo(r.annualExp().multiply(BigDecimal.TEN)) < 0) {
                return "low-row";
            }
            return null;
        });

        this.grid.getColumns().forEach(column -> {
            column.setAutoWidth(true);
            column.setFlexGrow(1);
        });

        this.grid.setAllRowsVisible(true);
        this.grid.setWidthFull();

        final var block = new VerticalLayout(new H2("Year-on-Year Projection"), this.grid);
        block.addClassName("grid-card");
        block.setPadding(false);
        block.setSpacing(true);
        block.setWidthFull();
        return block;
    }

    // ---- state transitions ---------------------------------------------

    private void onInputChanged() {
        this.store.save(this.prefs.currency(), this.form.getInputs());
    }

    private void onCurrencyOrPrefsChange() {
        applyForCurrency(this.prefs.currency());
        recalculate();
    }

    private void applyForCurrency(SupportedCurrency c) {
        RetirementInputs in = this.store.get(c);
        if (in == null) {
            in = this.defaults.forCurrency(c);
        }
        this.form.setInputs(in);
    }

    private void resetToDefaults() {
        final SupportedCurrency c = this.prefs.currency();
        final RetirementInputs defs = this.defaults.forCurrency(c);
        this.store.save(c, defs);
        this.form.setInputs(defs);
        recalculate();
    }

    private void recalculate() {
        final RetirementInputs in = this.form.getInputs();

        final RetirementResult r;
        try {
            r = RetirementCalculator.calculate(in);
        } catch (final IllegalArgumentException e) {
            this.corpusAtRetirement.setValue("—", null);
            this.expensesAtRetirement.setValue("—", null);
            this.lastsUntil.setValue("—", null);
            this.finalCorpus.setValue(e.getMessage(), Status.DANGER);
            return;
        }

        final SupportedCurrency c = this.prefs.currency();
        final ProjectionRow retire = r.rows().stream()
                .filter(ProjectionRow::isRetireYear).findFirst().orElse(null);
        if (retire != null) {
            this.corpusAtRetirement.setValue(MoneyFormatter.format(retire.startCorpus(), c), null);
            this.expensesAtRetirement.setValue(MoneyFormatter.format(retire.annualExp(), c), null);
        }

        if (r.corpusDepletedAt().isPresent()) {
            final int age = r.corpusDepletedAt().get() - 1;
            this.lastsUntil.setValue(age + " yrs", Status.DANGER);
            this.lastsUntil.setLabel("Corpus Lasts Until");
        } else {
            this.lastsUntil.setValue("Beyond " + in.lifeExp() + " yrs ✓", Status.SUCCESS);
        }

        final ProjectionRow lastRow = r.lastsUntilRow();
        final Status tone;
        if (lastRow.endCorpus().signum() <= 0) {
            tone = Status.DANGER;
        } else if (lastRow.endCorpus().compareTo(lastRow.annualExp().multiply(BigDecimal.valueOf(5))) < 0) {
            tone = Status.WARNING;
        } else {
            tone = Status.SUCCESS;
        }
        this.finalCorpus.setLabel(r.corpusDepletedAt().isPresent()
                ? "Final Corpus (at age " + lastRow.age() + ")"
                : "Final Corpus (at life expectancy)");
        this.finalCorpus.setValue(MoneyFormatter.format(lastRow.endCorpus(), c), tone);

        this.grid.setItems(r.rows());

        refreshCorpusChart(in, r);
        refreshExpensesChart(r);
        refreshInvestmentsChart(r);
    }

    private void refreshCorpusChart(RetirementInputs in, RetirementResult r) {
        final DataSeries series = new DataSeries("Corpus");
        series.add(new DataSeriesItem(in.currentAge(), in.corpus().doubleValue()));
        for (final ProjectionRow row : r.rows()) {
            series.add(new DataSeriesItem(row.age() + 1, Math.max(row.endCorpus().doubleValue(), 0)));
        }

        final PlotOptionsAreaspline plot = new PlotOptionsAreaspline();
        plot.setMarker(new Marker(false));

        final Configuration cfg = this.corpusChart.getConfiguration();
        cfg.setTitle("Corpus Trajectory");
        cfg.getChart().setStyledMode(true);
        cfg.getxAxis().setTitle("Age");
        cfg.getyAxis().setTitle(this.prefs.currency().name());
        cfg.setSeries(series);
        cfg.setPlotOptions(plot);

        final PlotLine retireLine = new PlotLine();
        retireLine.setValue(in.retireAge());
        retireLine.setColor(new SolidColor("#14b8a6"));
        retireLine.setDashStyle(DashStyle.SHORTDASH);
        retireLine.setWidth(2);

        final XAxis x = cfg.getxAxis();
        x.setPlotLines();
        x.addPlotLine(retireLine);

        r.corpusDepletedAt().ifPresent(age -> {
            final PlotLine pl = new PlotLine();
            pl.setValue(age);
            pl.setColor(new SolidColor("#ef4444"));
            pl.setDashStyle(DashStyle.SHORTDASH);
            pl.setWidth(2);
            x.addPlotLine(pl);
        });
        this.corpusChart.drawChart(true);
    }

    private void refreshExpensesChart(RetirementResult r) {
        final Configuration cfg = this.expensesChart.getConfiguration();
        cfg.setTitle("Annual Expenses");
        cfg.getChart().setStyledMode(true);
        cfg.getxAxis().setTitle("Age");
        cfg.getyAxis().setTitle(this.prefs.currency().name());

        final Number[] ys = new Number[r.rows().size()];
        final String[] cats = new String[r.rows().size()];
        for (int i = 0; i < r.rows().size(); i++) {
            ys[i] = r.rows().get(i).annualExp().doubleValue();
            cats[i] = Integer.toString(r.rows().get(i).age());
        }
        final ListSeries series = new ListSeries("Annual Expenses", ys);
        cfg.setSeries(series);
        cfg.getxAxis().setCategories(cats);
        this.expensesChart.drawChart(true);
    }

    private void refreshInvestmentsChart(RetirementResult r) {
        final double invested = r.investedAtRetirement().doubleValue();
        final ProjectionRow retire = r.rows().stream().filter(ProjectionRow::isRetireYear).findFirst().orElse(null);
        final double total = retire == null ? invested : retire.startCorpus().doubleValue();
        final double interest = Math.max(0, total - invested);

        final DataSeriesItem investedItem = new DataSeriesItem("Invested", invested);
        final DataSeriesItem interestItem = new DataSeriesItem("Interest", interest);

        final DataSeries series = new DataSeries();
        series.add(investedItem);
        series.add(interestItem);

        final PlotOptionsPie pie = new PlotOptionsPie();
        pie.setInnerSize("60%");

        final DataLabels dl = new DataLabels(true);
        dl.setFormat("{point.name}: {point.percentage:.1f}%");
        pie.setDataLabels(dl);

        final Configuration cfg = this.investmentsChart.getConfiguration();
        cfg.setTitle("Investments at Retirement");
        cfg.getChart().setStyledMode(true);
        cfg.setSeries(series);
        cfg.setPlotOptions(pie);
        this.investmentsChart.drawChart(true);
    }

    // ---- helpers --------------------------------------------------------

    private static String moneyOrDash(BigDecimal v, SupportedCurrency c) {
        if (v == null || v.signum() == 0) {
            return "—";
        }
        return MoneyFormatter.format(v, c);
    }

    private static Badge phaseBadge(boolean postRetirementPhase) {
        final Badge badge;
        if (postRetirementPhase) {
            badge = new Badge("Post");
        } else {
            badge = new Badge("Pre");
            badge.addThemeVariants(BadgeVariant.SUCCESS);
        }
        badge.addThemeVariants(BadgeVariant.SMALL);
        return badge;
    }
}
