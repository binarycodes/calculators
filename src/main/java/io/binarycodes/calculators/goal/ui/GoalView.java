package io.binarycodes.calculators.goal.ui;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.signals.Signal;
import io.binarycodes.calculators.base.common.Status;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.SummaryCard;
import io.binarycodes.calculators.goal.domain.GoalInputs;
import io.binarycodes.calculators.goal.domain.GoalResult;
import io.binarycodes.calculators.goal.service.GoalCalculator;
import io.binarycodes.calculators.goal.service.GoalDefaultsProvider;
import io.binarycodes.calculators.goal.service.GoalInputsStore;

/**
 * The goal-planner screen. Composes the input form, summary cards, growth
 * chart, and projection grid. The view itself owns no input fields or chart
 * configuration — those live in {@link GoalCalculatorForm}, {@link GoalGrowthChart},
 * and {@link GoalProjectionGrid}.
 */
@Route("goal")
@Menu(title = "Goal Planner", icon = "vaadin:bullseye", order = 2)
@PageTitle("Goal Planner")
public class GoalView extends VerticalLayout {

    private final UserPreferences preferences;
    private final GoalDefaultsProvider defaultsProvider;
    private final GoalInputsStore inputsStore;

    private final GoalCalculatorForm form;

    private final SummaryCard monthlyInvestment   = new SummaryCard("Monthly Investment");
    private final SummaryCard yearlyInvestment    = new SummaryCard("First-Year Investment");
    private final SummaryCard finalCorpus         = new SummaryCard("Final Corpus (gross)");
    private final SummaryCard taxAtExit           = new SummaryCard("Tax at Exit");

    private final GoalGrowthChart    growthChart    = new GoalGrowthChart();
    private final GoalProjectionGrid projectionGrid;

    private final VerticalLayout chartCard;
    private final VerticalLayout projectionCard;

    public GoalView(UserPreferences preferences,
                    GoalDefaultsProvider defaultsProvider,
                    GoalInputsStore inputsStore) {
        this.preferences = preferences;
        this.defaultsProvider = defaultsProvider;
        this.inputsStore = inputsStore;

        addClassName("goal-view");
        setWidthFull();
        setPadding(true);
        setSpacing(true);

        this.form = new GoalCalculatorForm(preferences);
        Signal.effect(this, context -> {
            this.form.inputsSignal().get();
            if (context.isInitialRun()) {
                return;
            }
            onInputChanged();
        });
        this.projectionGrid = new GoalProjectionGrid(preferences);

        add(new H2("Goal Planner"));
        add(this.form);
        add(buildActionRow());
        add(buildSummaryRow());
        this.chartCard = buildChartCard();
        add(this.chartCard);
        this.projectionCard = buildProjectionCard();
        add(this.projectionCard);

        preferences.addChangeListener(ignored -> onPreferencesChanged());
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
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
                this.monthlyInvestment, this.yearlyInvestment,
                this.finalCorpus, this.taxAtExit);
        summaryRow.addClassName("summary-row");
        summaryRow.setWidthFull();
        summaryRow.setFlexGrow(1,
                this.monthlyInvestment, this.yearlyInvestment,
                this.finalCorpus, this.taxAtExit);
        summaryRow.setSpacing(true);
        return summaryRow;
    }

    private VerticalLayout buildChartCard() {
        final VerticalLayout card = new VerticalLayout(this.growthChart);
        card.addClassName("chart-card");
        card.setPadding(false);
        card.setSpacing(false);
        card.setWidthFull();
        return card;
    }

    private VerticalLayout buildProjectionCard() {
        final H2 title = new H2("Year-on-Year Projection");
        final HorizontalLayout header = new HorizontalLayout(title,
                this.projectionGrid.createColumnChooser());
        header.setWidthFull();
        header.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(
                com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode.BETWEEN);

        final VerticalLayout card = new VerticalLayout(header, this.projectionGrid);
        card.addClassName("grid-card");
        card.setPadding(false);
        card.setSpacing(true);
        card.setWidthFull();
        return card;
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
        GoalInputs inputs = this.inputsStore.get(currency);
        if (inputs == null) {
            inputs = this.defaultsProvider.forCurrency(currency);
        }
        this.form.setInputs(inputs);
    }

    private void resetToDefaults() {
        final SupportedCurrency currency = this.preferences.currency();
        final GoalInputs defaultInputs = this.defaultsProvider.forCurrency(currency);
        this.inputsStore.save(currency, defaultInputs);
        this.form.setInputs(defaultInputs);
        recalculate();
    }

    private void recalculate() {
        if (!this.form.investmentsCard().isAllocationValid()) {
            showInvalidFormPlaceholders("Allocations must sum to 100%.");
            return;
        }
        if (!this.form.isValid()) {
            this.form.validate();
            showInvalidFormPlaceholders("Fix the highlighted fields to recalculate.");
            return;
        }

        final GoalInputs inputs = this.form.getInputs();
        final GoalResult result;
        try {
            result = GoalCalculator.calculate(inputs);
        } catch (final IllegalArgumentException invalid) {
            showInvalidFormPlaceholders(invalid.getMessage());
            return;
        }

        final SupportedCurrency currency = this.preferences.currency();
        this.form.setInflationHelperText(
                "Target at horizon: " + MoneyFormatter.format(result.inflatedGoal(), currency));

        if (result.goalAlreadyCovered()) {
            this.monthlyInvestment.setValue("—", Status.SUCCESS);
            this.yearlyInvestment.setValue("Goal already covered ✓", Status.SUCCESS);
            this.finalCorpus.setValue(MoneyFormatter.format(result.finalBalance(), currency), Status.SUCCESS);
            this.taxAtExit.setValue(MoneyFormatter.format(result.taxAtExit(), currency), null);
            this.chartCard.setVisible(false);
            this.projectionCard.setVisible(false);
            return;
        }

        this.monthlyInvestment.setValue(MoneyFormatter.format(result.monthlyInvestment(), currency), null);
        this.yearlyInvestment.setValue(MoneyFormatter.format(result.firstYearInvestment(), currency), null);
        this.finalCorpus.setValue(MoneyFormatter.format(result.finalBalance(), currency), Status.SUCCESS);
        this.taxAtExit.setValue(MoneyFormatter.format(result.taxAtExit(), currency), null);

        this.chartCard.setVisible(true);
        this.projectionCard.setVisible(true);
        this.growthChart.update(result, currency);
        this.projectionGrid.update(result.rows());
    }

    private void showInvalidFormPlaceholders(String dangerMessage) {
        this.monthlyInvestment.setValue("—", null);
        this.yearlyInvestment.setValue("—", null);
        this.finalCorpus.setValue("—", null);
        this.taxAtExit.setValue(dangerMessage, Status.DANGER);
        this.form.setInflationHelperText("");
        this.chartCard.setVisible(false);
        this.projectionCard.setVisible(false);
    }
}
