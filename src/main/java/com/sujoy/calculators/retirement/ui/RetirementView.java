package com.sujoy.calculators.retirement.ui;

import com.sujoy.calculators.base.money.Currency;
import com.sujoy.calculators.base.money.MoneyFormatter;
import com.sujoy.calculators.base.prefs.UserPreferences;
import com.sujoy.calculators.base.ui.MoneyField;
import com.sujoy.calculators.retirement.domain.ProjectionRow;
import com.sujoy.calculators.retirement.domain.RetirementInputs;
import com.sujoy.calculators.retirement.domain.RetirementResult;
import com.sujoy.calculators.retirement.service.DefaultsProvider;
import com.sujoy.calculators.retirement.service.RetirementCalculator;
import com.sujoy.calculators.retirement.service.RetirementInputsStore;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.component.charts.model.Configuration;
import com.vaadin.flow.component.charts.model.DataLabels;
import com.vaadin.flow.component.charts.model.DataSeries;
import com.vaadin.flow.component.charts.model.DataSeriesItem;
import com.vaadin.flow.component.charts.model.ListSeries;
import com.vaadin.flow.component.charts.model.PlotLine;
import com.vaadin.flow.component.charts.model.PlotOptionsAreaspline;
import com.vaadin.flow.component.charts.model.PlotOptionsPie;
import com.vaadin.flow.component.charts.model.XAxis;
import com.vaadin.flow.component.charts.model.YAxis;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.formlayout.FormLayout.ResponsiveStep;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The retirement calculator screen. Mirrors the original
 * {@code retirement-calculator.html} layout: timeline / current finances /
 * corpus returns / monthly SIP inputs, summary cards, three charts, and a
 * year-by-year projection grid.
 */
@Route("retirement")
@RouteAlias("")
@Menu(title = "Retirement", icon = "vaadin:piggy-bank", order = 1)
@PageTitle("Retirement Calculator")
public class RetirementView extends VerticalLayout {

    private final UserPreferences prefs;
    private final DefaultsProvider defaults;
    private final RetirementInputsStore store;

    // Inputs ---------------------------------------------------------------
    private final IntegerField currentAge = ageField("Current Age");
    private final IntegerField retireAge  = ageField("Retirement Age");
    private final IntegerField lifeExp    = ageField("Life Expectancy");

    private final MoneyField corpus;
    private final MoneyField monthlyExp;
    private final MoneyField monthlyInvPre;
    private final MoneyField monthlyInvPost;

    private final NumberField inflation     = pctField("Inflation Rate");
    private final NumberField growthPre     = pctField("Returns (Before retirement)");
    private final NumberField growthPost    = pctField("Returns (After retirement)");
    private final NumberField sipGrowthPre  = pctField("SIP Returns (Before retirement)");
    private final NumberField sipGrowthPost = pctField("SIP Returns (After retirement)");

    // Outputs --------------------------------------------------------------
    private final SummaryCard corpusAtRetirement = new SummaryCard("Corpus at Retirement");
    private final SummaryCard expensesAtRetirement = new SummaryCard("Annual Expenses at Retirement");
    private final SummaryCard lastsUntil           = new SummaryCard("Corpus Lasts Until");
    private final SummaryCard finalCorpus          = new SummaryCard("Final Corpus");

    private final Chart corpusChart    = new Chart(ChartType.AREASPLINE);
    private final Chart expensesChart  = new Chart(ChartType.AREASPLINE);
    private final Chart investmentsChart = new Chart(ChartType.PIE);

    private final Grid<ProjectionRow> grid = new Grid<>(ProjectionRow.class, false);

    private boolean suspendListeners;

    public RetirementView(UserPreferences prefs, DefaultsProvider defaults, RetirementInputsStore store) {
        this.prefs    = prefs;
        this.defaults = defaults;
        this.store    = store;

        addClassName("retirement-view");
        setWidthFull();
        setPadding(true);
        setSpacing(true);

        this.corpus = new MoneyField("Current Corpus",                       prefs);
        this.monthlyExp = new MoneyField("Monthly Expenses (today)",             prefs);
        this.monthlyInvPre = new MoneyField("Monthly SIP (Before retirement)",      prefs);
        this.monthlyInvPost = new MoneyField("Monthly SIP (After retirement)",       prefs);

        add(new H2("Retirement Calculator"));
        add(buildInputForm());
        add(buildActions());
        add(buildSummary());
        add(buildChartsBlock());
        add(buildGridBlock());

        // React to currency switches: re-pull inputs for the new currency,
        // re-format helper text & money values, recalculate.
        prefs.addChangeListener(p -> onCurrencyOrPrefsChange());
        attachInputListeners();
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

    // ---- form layout -----------------------------------------------------

    private VerticalLayout buildInputForm() {
        final Details timeline = section("Timeline",
                this.currentAge, this.retireAge, this.lifeExp);
        final Details current = section("Current Finances",
                this.corpus, this.monthlyExp, asPctSuffix(this.inflation));
        final Details corpusReturns = section("Existing Corpus Returns",
                asPctSuffix(this.growthPre), asPctSuffix(this.growthPost));
        final Details sip = section("Monthly SIP Contributions",
                this.monthlyInvPre, asPctSuffix(this.sipGrowthPre),
                this.monthlyInvPost, asPctSuffix(this.sipGrowthPost));
        final VerticalLayout col = new VerticalLayout(timeline, current, corpusReturns, sip);
        col.setPadding(false);
        col.setSpacing(true);
        col.setWidthFull();
        return col;
    }

    private Details section(String title, com.vaadin.flow.component.Component... fields) {
        final FormLayout inner = new FormLayout();
        inner.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0",    1),
                new FormLayout.ResponsiveStep("36em", 2),
                new FormLayout.ResponsiveStep("64em", 3),
                new FormLayout.ResponsiveStep("90em", 4));
        inner.add(fields);
        final Details d = new Details(title, inner);
        d.setOpened(true);
        d.setWidthFull();
        d.addClassName("form-section");
        return d;
    }

    private NumberField asPctSuffix(NumberField nf) {
        if (nf.getSuffixComponent() == null) {
            final Span s = new Span("%");
            s.getStyle().setColor("var(--vaadin-secondary-text-color, #71717a)");
            nf.setSuffixComponent(s);
        }
        return nf;
    }

    private HorizontalLayout buildActions() {
        final Button calc = new Button("Calculate", e -> recalculate());
        calc.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        final Button reset = new Button("Reset", e -> resetToDefaults());
        final HorizontalLayout row = new HorizontalLayout(calc, reset);
        row.addClassName("action-row");
        row.setSpacing(true);
        return row;
    }

    // ---- summary --------------------------------------------------------

    private HorizontalLayout buildSummary() {
        final HorizontalLayout row = new HorizontalLayout(
                this.corpusAtRetirement, this.expensesAtRetirement, this.lastsUntil, this.finalCorpus);
        row.addClassName("summary-row");
        row.setWidthFull();
        row.setFlexGrow(1, this.corpusAtRetirement, this.expensesAtRetirement, this.lastsUntil, this.finalCorpus);
        row.setSpacing(true);
        return row;
    }

    // ---- charts ---------------------------------------------------------

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
                case "Corpus"           -> host.add(this.corpusChart);
                case "Annual Expenses"  -> host.add(this.expensesChart);
                case "Investments"      -> host.add(this.investmentsChart);
            }
        });

        final VerticalLayout block = new VerticalLayout(tabs, host);
        block.addClassName("chart-card");
        block.setPadding(false);
        block.setSpacing(false);
        block.setWidthFull();
        return block;
    }

    // ---- grid -----------------------------------------------------------

    private VerticalLayout buildGridBlock() {
        // Narrow columns get auto-width so they fit tightly; numeric columns get
        // flex-grow so the grid fills the container without leaving whitespace
        // on the right.
        this.grid.addColumn(ProjectionRow::year).setHeader("Year").setAutoWidth(true).setFlexGrow(0);
        this.grid.addColumn(ProjectionRow::age).setHeader("Age").setAutoWidth(true).setFlexGrow(0);
        this.grid.addColumn(r -> r.isPost() ? "Post" : "Pre").setHeader("Phase").setAutoWidth(true).setFlexGrow(0);
        this.grid.addColumn(r -> moneyOrDash(r.annualExp(), this.prefs.currency())).setHeader("Annual Expenses").setFlexGrow(1).setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);
        this.grid.addColumn(r -> moneyOrDash(r.startCorpus(), this.prefs.currency())).setHeader("Corpus (Start)").setFlexGrow(1).setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);
        this.grid.addColumn(r -> moneyOrDash(r.returns(), this.prefs.currency())).setHeader("Returns").setFlexGrow(1).setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);
        this.grid.addColumn(r -> moneyOrDash(r.investment(), this.prefs.currency())).setHeader("Investment").setFlexGrow(1).setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);
        this.grid.addColumn(r -> moneyOrDash(r.withdrawal(), this.prefs.currency())).setHeader("Withdrawal").setFlexGrow(1).setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);
        this.grid.addColumn(r -> moneyOrDash(r.endCorpus(), this.prefs.currency()))
                .setHeader("Corpus (End)").setFlexGrow(1)
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setPartNameGenerator(r -> {
                    if (!r.isPost()) return null;
                    if (r.endCorpus().signum() <= 0) return "corpus-end-depleted";
                    if (r.endCorpus().compareTo(r.annualExp().multiply(BigDecimal.TEN)) < 0)
                        return "corpus-end-low";
                    return "corpus-end-healthy";
                });
        this.grid.setPartNameGenerator(r -> {
            if (r.depleted())      return "depleted-row";
            if (r.isRetireYear())  return "retirement-row";
            if (r.isPost() && r.endCorpus().compareTo(r.annualExp().multiply(BigDecimal.TEN)) < 0)
                return "low-row";
            return null;
        });
        this.grid.setAllRowsVisible(true);
        this.grid.setWidthFull();
        final VerticalLayout block = new VerticalLayout(new H2("Year-on-Year Projection"), this.grid);
        block.addClassName("grid-card");
        block.setPadding(false);
        block.setSpacing(true);
        block.setWidthFull();
        return block;
    }

    // ---- input wiring ---------------------------------------------------

    private static IntegerField ageField(String label) {
        final IntegerField f = new IntegerField(label);
        f.setMin(1);
        f.setMax(120);
        f.setStepButtonsVisible(false);
        final Span suffix = new Span("yrs");
        suffix.getStyle().setColor("var(--vaadin-secondary-text-color, #71717a)");
        f.setSuffixComponent(suffix);
        return f;
    }

    private static NumberField pctField(String label) {
        final NumberField f = new NumberField(label);
        f.setMin(0);
        f.setMax(100);
        f.setStep(0.1);
        f.setStepButtonsVisible(false);
        return f;
    }

    private void attachInputListeners() {
        java.util.List.of(this.currentAge, this.retireAge, this.lifeExp).forEach(
                f -> f.addValueChangeListener(e -> onInputChanged()));
        java.util.List.of(this.inflation, this.growthPre, this.growthPost, this.sipGrowthPre, this.sipGrowthPost).forEach(
                f -> f.addValueChangeListener(e -> onInputChanged()));
        java.util.List.of(this.corpus, this.monthlyExp, this.monthlyInvPre, this.monthlyInvPost).forEach(
                f -> f.addValueChangeListener(e -> onInputChanged()));
    }

    private void onInputChanged() {
        if (this.suspendListeners) return;
        this.store.save(this.prefs.currency(), readInputs());
    }

    // ---- state transitions ---------------------------------------------

    private void onCurrencyOrPrefsChange() {
        applyForCurrency(this.prefs.currency());
        recalculate();
    }

    private void applyForCurrency(Currency c) {
        RetirementInputs in = this.store.get(c);
        if (in == null) in = this.defaults.forCurrency(c);
        writeInputs(in);
    }

    private void resetToDefaults() {
        final Currency c = this.prefs.currency();
        final RetirementInputs defs = this.defaults.forCurrency(c);
        this.store.save(c, defs);
        writeInputs(defs);
        recalculate();
    }

    private void writeInputs(RetirementInputs in) {
        this.suspendListeners = true;
        try {
            this.currentAge.setValue(in.currentAge());
            this.retireAge.setValue(in.retireAge());
            this.lifeExp.setValue(in.lifeExp());
            this.corpus.setBigDecimal(in.corpus());
            this.monthlyExp.setBigDecimal(in.monthlyExpenses());
            this.monthlyInvPre.setBigDecimal(in.monthlyInvPre());
            this.monthlyInvPost.setBigDecimal(in.monthlyInvPost());
            this.inflation.setValue(toDouble(in.inflationPct()));
            this.growthPre.setValue(toDouble(in.growthPrePct()));
            this.growthPost.setValue(toDouble(in.growthPostPct()));
            this.sipGrowthPre.setValue(toDouble(in.sipGrowthPrePct()));
            this.sipGrowthPost.setValue(toDouble(in.sipGrowthPostPct()));
        } finally {
            this.suspendListeners = false;
        }
    }

    private RetirementInputs readInputs() {
        return new RetirementInputs(
                nz(this.currentAge.getValue(), 35),
                nz(this.retireAge.getValue(), 60),
                nz(this.lifeExp.getValue(), 90),
                or0(this.corpus.getValue()),
                or0(this.monthlyExp.getValue()),
                bd(this.inflation.getValue()),
                bd(this.growthPre.getValue()),
                bd(this.growthPost.getValue()),
                or0(this.monthlyInvPre.getValue()),
                bd(this.sipGrowthPre.getValue()),
                or0(this.monthlyInvPost.getValue()),
                bd(this.sipGrowthPost.getValue())
        );
    }

    private void recalculate() {
        final RetirementInputs in;
        try {
            in = readInputs();
        } catch (final Exception e) { return; }

        final RetirementResult r;
        try {
            r = RetirementCalculator.calculate(in);
        } catch (final IllegalArgumentException e) {
            this.corpusAtRetirement.setValue("—", null);
            this.expensesAtRetirement.setValue("—", null);
            this.lastsUntil.setValue("—", null);
            this.finalCorpus.setValue(e.getMessage(), "red");
            return;
        }

        final Currency c = this.prefs.currency();
        final ProjectionRow retire = r.rows().stream()
                .filter(ProjectionRow::isRetireYear).findFirst().orElse(null);
        if (retire != null) {
            this.corpusAtRetirement.setValue(MoneyFormatter.format(retire.startCorpus(), c), null);
            this.expensesAtRetirement.setValue(MoneyFormatter.format(retire.annualExp(), c), null);
        }

        if (r.corpusDepletedAt().isPresent()) {
            final int age = r.corpusDepletedAt().get() - 1;
            this.lastsUntil.setValue(age + " yrs", "red");
            this.lastsUntil.setLabel("Corpus Lasts Until");
        } else {
            this.lastsUntil.setValue("Beyond " + in.lifeExp() + " yrs ✓", "green");
        }

        final ProjectionRow lastRow = r.lastsUntilRow();
        final String tone;
        if (lastRow.endCorpus().signum() <= 0) tone = "red";
        else if (lastRow.endCorpus().compareTo(lastRow.annualExp().multiply(BigDecimal.valueOf(5))) < 0) tone = "amber";
        else tone = "green";
        this.finalCorpus.setLabel(r.corpusDepletedAt().isPresent()
                ? "Final Corpus (at age " + lastRow.age() + ")"
                : "Final Corpus (at life expectancy)");
        this.finalCorpus.setValue(MoneyFormatter.format(lastRow.endCorpus(), c), tone);

        this.grid.setItems(r.rows());

        refreshCorpusChart(in, r);
        refreshExpensesChart(r);
        refreshInvestmentsChart(r);
    }

    // ---- chart rendering ------------------------------------------------

    private void refreshCorpusChart(RetirementInputs in, RetirementResult r) {
        final Configuration cfg = this.corpusChart.getConfiguration();
        cfg.setTitle("Corpus Trajectory");
        cfg.getxAxis().setTitle("Age");
        cfg.getyAxis().setTitle(this.prefs.currency().name());
        final DataSeries series = new DataSeries("Corpus");
        series.add(new DataSeriesItem(in.currentAge(), in.corpus().doubleValue()));
        for (final ProjectionRow row : r.rows()) {
            series.add(new DataSeriesItem(row.age() + 1, Math.max(row.endCorpus().doubleValue(), 0)));
        }
        cfg.setSeries(series);
        final PlotOptionsAreaspline plot = new PlotOptionsAreaspline();
        plot.setMarker(new com.vaadin.flow.component.charts.model.Marker(false));
        cfg.setPlotOptions(plot);

        final XAxis x = cfg.getxAxis();
        x.setPlotLines();
        final PlotLine retireLine = new PlotLine();
        retireLine.setValue(in.retireAge());
        retireLine.setColor(new com.vaadin.flow.component.charts.model.style.SolidColor("#14b8a6"));
        retireLine.setDashStyle(com.vaadin.flow.component.charts.model.DashStyle.SHORTDASH);
        retireLine.setWidth(2);
        x.addPlotLine(retireLine);
        r.corpusDepletedAt().ifPresent(age -> {
            final PlotLine pl = new PlotLine();
            pl.setValue(age);
            pl.setColor(new com.vaadin.flow.component.charts.model.style.SolidColor("#ef4444"));
            pl.setDashStyle(com.vaadin.flow.component.charts.model.DashStyle.SHORTDASH);
            pl.setWidth(2);
            x.addPlotLine(pl);
        });
        this.corpusChart.drawChart(true);
    }

    private void refreshExpensesChart(RetirementResult r) {
        final Configuration cfg = this.expensesChart.getConfiguration();
        cfg.setTitle("Annual Expenses");
        cfg.getxAxis().setTitle("Age");
        cfg.getyAxis().setTitle(this.prefs.currency().name());
        final Number[] ys = new Number[r.rows().size()];
        final String[] cats = new String[r.rows().size()];
        for (int i = 0; i < r.rows().size(); i++) {
            ys[i]   = r.rows().get(i).annualExp().doubleValue();
            cats[i] = Integer.toString(r.rows().get(i).age());
        }
        final ListSeries series = new ListSeries("Annual Expenses", ys);
        cfg.setSeries(series);
        cfg.getxAxis().setCategories(cats);
        this.expensesChart.drawChart(true);
    }

    private void refreshInvestmentsChart(RetirementResult r) {
        final Configuration cfg = this.investmentsChart.getConfiguration();
        cfg.setTitle("Investments at Retirement");
        final double invested = r.investedAtRetirement().doubleValue();
        final ProjectionRow retire = r.rows().stream().filter(ProjectionRow::isRetireYear).findFirst().orElse(null);
        final double total = retire == null ? invested : retire.startCorpus().doubleValue();
        final double interest = Math.max(0, total - invested);
        final DataSeries series = new DataSeries();
        final DataSeriesItem investedItem = new DataSeriesItem("Invested", invested);
        final DataSeriesItem interestItem  = new DataSeriesItem("Interest", interest);
        series.add(investedItem);
        series.add(interestItem);
        cfg.setSeries(series);
        final PlotOptionsPie pie = new PlotOptionsPie();
        pie.setInnerSize("60%");
        final DataLabels dl = new DataLabels(true);
        dl.setFormat("{point.name}: {point.percentage:.1f}%");
        pie.setDataLabels(dl);
        cfg.setPlotOptions(pie);
        this.investmentsChart.drawChart(true);
    }

    // ---- helpers --------------------------------------------------------

    private static String moneyOrDash(BigDecimal v, Currency c) {
        if (v == null || v.signum() == 0) return "—";
        return MoneyFormatter.format(v, c);
    }

    private static int nz(Integer v, int dflt) { return v == null ? dflt : v; }

    private static BigDecimal bd(Double v) {
        return v == null ? BigDecimal.ZERO : BigDecimal.valueOf(v);
    }

    private static BigDecimal or0(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static double toDouble(BigDecimal bd) {
        return bd == null ? 0 : bd.setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
