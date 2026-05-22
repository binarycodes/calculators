package io.binarycodes.calculators.retirement.ui;


import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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
 * The retirement calculator screen. Composes the input form, summary cards,
 * charts, and projection grid; owns no input fields, chart configuration, or
 * grid columns directly — those live in {@link RetirementCalculatorForm},
 * {@link CorpusChart}, {@link ExpensesChart}, {@link InvestmentsChart},
 * {@link ReturnOnInvestmentsChart}, and {@link ProjectionGrid}.
 */
@Route("retirement")
@RouteAlias("")
@Menu(title = "Retirement", icon = "vaadin:piggy-bank", order = 1)
@PageTitle("Retirement Calculator")
public class RetirementView extends VerticalLayout {

    private static final BigDecimal HEALTHY_CORPUS_MULTIPLIER = BigDecimal.valueOf(5);

    private final UserPreferences preferences;
    private final DefaultsProvider defaultsProvider;
    private final RetirementInputsStore inputsStore;

    private final RetirementCalculatorForm form;

    private final SummaryCard corpusAtRetirement   = new SummaryCard("Corpus at Retirement");
    private final SummaryCard expensesAtRetirement = new SummaryCard("Annual Expenses at Retirement");
    private final SummaryCard lastsUntil           = new SummaryCard("Corpus Lasts Until");
    private final SummaryCard finalCorpus          = new SummaryCard("Final Corpus");

    private final CorpusChart                corpusChart                = new CorpusChart();
    private final ExpensesChart              expensesChart              = new ExpensesChart();
    private final InvestmentsChart           investmentsChart           = new InvestmentsChart();
    private final ReturnOnInvestmentsChart   returnOnInvestmentsChart   = new ReturnOnInvestmentsChart();

    private final ProjectionGrid projectionGrid;

    public RetirementView(UserPreferences preferences,
                          DefaultsProvider defaultsProvider,
                          RetirementInputsStore inputsStore) {
        this.preferences = preferences;
        this.defaultsProvider = defaultsProvider;
        this.inputsStore = inputsStore;

        addClassName("retirement-view");
        setWidthFull();
        setPadding(true);
        setSpacing(true);

        this.form = new RetirementCalculatorForm(preferences);
        this.form.addInputChangeListener(this::onInputChanged);
        this.projectionGrid = new ProjectionGrid(preferences);

        add(new H2("Retirement Calculator"));
        add(this.form);
        add(buildActionRow());
        add(buildSummaryRow());
        add(buildChartsCard());
        add(buildProjectionGridCard());

        preferences.addChangeListener(ignored -> onPreferencesChanged());
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        // Browser localStorage is read asynchronously from the client, so the
        // initial population has to wait until both prefs and inputs have loaded.
        this.preferences.loadFromBrowser(() ->
                this.inputsStore.load(unused -> {
                    populateFormFromPersistedOrDefault(this.preferences.currency());
                    recalculate();
                })
        );
    }

    private HorizontalLayout buildActionRow() {
        final Button calculateButton = new Button("Calculate", event -> recalculate());
        calculateButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        final Button resetButton = new Button("Reset", event -> resetToDefaults());

        final HorizontalLayout actionRow = new HorizontalLayout(calculateButton, resetButton);
        actionRow.addClassName("action-row");
        actionRow.setSpacing(true);
        return actionRow;
    }

    private HorizontalLayout buildSummaryRow() {
        final HorizontalLayout summaryRow = new HorizontalLayout(
                this.corpusAtRetirement, this.expensesAtRetirement,
                this.lastsUntil, this.finalCorpus);
        summaryRow.addClassName("summary-row");
        summaryRow.setWidthFull();
        summaryRow.setFlexGrow(1,
                this.corpusAtRetirement, this.expensesAtRetirement,
                this.lastsUntil, this.finalCorpus);
        summaryRow.setSpacing(true);
        return summaryRow;
    }

    private VerticalLayout buildChartsCard() {
        final Tabs chartTabs = new Tabs(
                new Tab("Corpus"),
                new Tab("Annual Expenses"),
                new Tab("Investments"),
                new Tab("Return on Investments"));

        final VerticalLayout activeChartContainer = new VerticalLayout();
        activeChartContainer.setPadding(false);
        activeChartContainer.setSpacing(false);
        activeChartContainer.setSizeFull();
        activeChartContainer.add(this.corpusChart);

        chartTabs.addSelectedChangeListener(event -> {
            activeChartContainer.removeAll();
            switch (event.getSelectedTab().getLabel()) {
                case "Corpus"                -> activeChartContainer.add(this.corpusChart);
                case "Annual Expenses"       -> activeChartContainer.add(this.expensesChart);
                case "Investments"           -> activeChartContainer.add(this.investmentsChart);
                case "Return on Investments" -> activeChartContainer.add(this.returnOnInvestmentsChart);
            }
        });

        final VerticalLayout chartsCard = new VerticalLayout(chartTabs, activeChartContainer);
        chartsCard.addClassName("chart-card");
        chartsCard.setPadding(false);
        chartsCard.setSpacing(false);
        chartsCard.setWidthFull();
        return chartsCard;
    }

    private VerticalLayout buildProjectionGridCard() {
        final VerticalLayout gridCard = new VerticalLayout(
                new H2("Year-on-Year Projection"), this.projectionGrid);
        gridCard.addClassName("grid-card");
        gridCard.setPadding(false);
        gridCard.setSpacing(true);
        gridCard.setWidthFull();
        return gridCard;
    }

    private void onInputChanged() {
        this.inputsStore.save(this.preferences.currency(), this.form.getInputs());
        recalculate();
    }

    private void onPreferencesChanged() {
        populateFormFromPersistedOrDefault(this.preferences.currency());
        recalculate();
    }

    private void populateFormFromPersistedOrDefault(SupportedCurrency currency) {
        RetirementInputs inputs = this.inputsStore.get(currency);
        if (inputs == null) {
            inputs = this.defaultsProvider.forCurrency(currency);
        }
        this.form.setInputs(inputs);
    }

    private void resetToDefaults() {
        final SupportedCurrency currency = this.preferences.currency();
        final RetirementInputs defaultInputs = this.defaultsProvider.forCurrency(currency);
        this.inputsStore.save(currency, defaultInputs);
        this.form.setInputs(defaultInputs);
        recalculate();
    }

    private void recalculate() {
        if (!this.form.isValid()) {
            this.form.validate();
            showInvalidFormPlaceholders("Fix the highlighted fields to recalculate.");
            return;
        }

        final RetirementInputs inputs = this.form.getInputs();

        final RetirementResult result;
        try {
            result = RetirementCalculator.calculate(inputs);
        } catch (final IllegalArgumentException invalid) {
            showInvalidFormPlaceholders(invalid.getMessage());
            return;
        }

        final SupportedCurrency currency = this.preferences.currency();
        updateRetirementYearSummaries(result, currency);
        updateLastsUntilSummary(result, inputs.getLifeExp());
        updateFinalCorpusSummary(result, currency);

        this.projectionGrid.update(result.rows());

        this.corpusChart.update(inputs, result, currency);
        this.expensesChart.update(result, currency);
        this.investmentsChart.update(result, currency);
        this.returnOnInvestmentsChart.update(inputs, result, currency);
    }

    private void showInvalidFormPlaceholders(String dangerMessage) {
        this.corpusAtRetirement.setValue("—", null);
        this.expensesAtRetirement.setValue("—", null);
        this.lastsUntil.setValue("—", null);
        this.finalCorpus.setValue(dangerMessage, Status.DANGER);
    }

    private void updateRetirementYearSummaries(RetirementResult result, SupportedCurrency currency) {
        final ProjectionRow retirementRow = result.rows().stream()
                .filter(ProjectionRow::isRetireYear).findFirst().orElse(null);
        if (retirementRow == null) {
            return;
        }
        this.corpusAtRetirement.setValue(MoneyFormatter.format(retirementRow.startCorpus(), currency), null);
        this.expensesAtRetirement.setValue(MoneyFormatter.format(retirementRow.annualExp(), currency), null);
    }

    private void updateLastsUntilSummary(RetirementResult result, int lifeExpectancy) {
        if (result.corpusDepletedAt().isPresent()) {
            final int lastFullyCoveredAge = result.corpusDepletedAt().get() - 1;
            this.lastsUntil.setLabel("Corpus Lasts Until");
            this.lastsUntil.setValue(lastFullyCoveredAge + " yrs", Status.DANGER);
        } else {
            this.lastsUntil.setValue("Beyond " + lifeExpectancy + " yrs ✓", Status.SUCCESS);
        }
    }

    private void updateFinalCorpusSummary(RetirementResult result, SupportedCurrency currency) {
        final ProjectionRow lastsUntilRow = result.lastsUntilRow();
        final Status tone = finalCorpusTone(lastsUntilRow);
        this.finalCorpus.setLabel(result.corpusDepletedAt().isPresent()
                ? "Final Corpus (at age " + lastsUntilRow.age() + ")"
                : "Final Corpus (at life expectancy)");
        this.finalCorpus.setValue(MoneyFormatter.format(lastsUntilRow.endCorpus(), currency), tone);
    }

    private static Status finalCorpusTone(ProjectionRow lastsUntilRow) {
        if (lastsUntilRow.endCorpus().signum() <= 0) {
            return Status.DANGER;
        }
        final BigDecimal healthyThreshold =
                lastsUntilRow.annualExp().multiply(HEALTHY_CORPUS_MULTIPLIER);
        if (lastsUntilRow.endCorpus().compareTo(healthyThreshold) < 0) {
            return Status.WARNING;
        }
        return Status.SUCCESS;
    }
}
