package io.binarycodes.calculators.investment.ui;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
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
import io.binarycodes.calculators.investment.domain.InvestmentInputs;
import io.binarycodes.calculators.investment.domain.InvestmentResult;
import io.binarycodes.calculators.investment.service.InvestmentCalculator;
import io.binarycodes.calculators.investment.service.InvestmentDefaultsProvider;
import io.binarycodes.calculators.investment.service.InvestmentInputsStore;

/**
 * The investment-calculator screen. Composes the input form, four summary
 * cards, the corpus build-up chart, and the year-by-year projection grid.
 */
@Route("investment")
@Menu(title = "Investment", icon = "vaadin:coin-piles", order = 4)
@PageTitle("Investment Calculator")
public class InvestmentView extends VerticalLayout {

    private final UserPreferences preferences;
    private final InvestmentDefaultsProvider defaultsProvider;
    private final InvestmentInputsStore inputsStore;

    private final InvestmentCalculatorForm form;

    private final SummaryCard investedCard = new SummaryCard("Total Invested");
    private final SummaryCard maturityCard = new SummaryCard("Maturity Value");
    private final SummaryCard netCard = new SummaryCard("Net After Tax");
    private final SummaryCard buyingPowerCard = new SummaryCard("Buying Power Today");

    private final InvestmentGrowthChart chart = new InvestmentGrowthChart();
    private final InvestmentProjectionGrid projectionGrid;

    private final VerticalLayout chartCard;
    private final VerticalLayout projectionCard;

    public InvestmentView(UserPreferences preferences,
                          InvestmentDefaultsProvider defaultsProvider,
                          InvestmentInputsStore inputsStore) {
        this.preferences = preferences;
        this.defaultsProvider = defaultsProvider;
        this.inputsStore = inputsStore;

        addClassName("investment-view");
        setWidthFull();
        setPadding(true);
        setSpacing(true);

        this.form = new InvestmentCalculatorForm(preferences);
        Signal.effect(this, context -> {
            this.form.inputsSignal().get();
            if (context.isInitialRun()) {
                return;
            }
            onInputChanged();
        });
        this.projectionGrid = new InvestmentProjectionGrid(preferences);

        add(new H2("Investment Calculator"));
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
                this.investedCard, this.maturityCard, this.netCard, this.buyingPowerCard);
        summaryRow.addClassName("summary-row");
        summaryRow.setWidthFull();
        summaryRow.setFlexGrow(1,
                this.investedCard, this.maturityCard, this.netCard, this.buyingPowerCard);
        summaryRow.setSpacing(true);
        return summaryRow;
    }

    private VerticalLayout buildChartCard() {
        final VerticalLayout card = new VerticalLayout(this.chart);
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
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

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
        InvestmentInputs inputs = this.inputsStore.get(currency);
        if (inputs == null) {
            inputs = this.defaultsProvider.forCurrency(currency);
        }
        this.form.setInputs(inputs);
    }

    private void resetToDefaults() {
        final SupportedCurrency currency = this.preferences.currency();
        final InvestmentInputs defaultInputs = this.defaultsProvider.forCurrency(currency);
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

        final InvestmentInputs inputs = this.form.getInputs();
        final InvestmentResult result;
        try {
            result = InvestmentCalculator.calculate(inputs);
        } catch (final IllegalArgumentException invalid) {
            showInvalidFormPlaceholders(invalid.getMessage());
            return;
        }

        final SupportedCurrency currency = this.preferences.currency();
        this.investedCard.setValue(MoneyFormatter.format(result.totalInvested(), currency), null);
        this.maturityCard.setValue(MoneyFormatter.format(result.maturityValue(), currency), null);
        this.netCard.setValue(MoneyFormatter.format(result.netValue(), currency), null);
        this.buyingPowerCard.setValue(MoneyFormatter.format(result.buyingPowerToday(), currency), null);

        this.chartCard.setVisible(true);
        this.projectionCard.setVisible(true);
        this.chart.update(result, currency);
        this.projectionGrid.update(result.rows());
    }

    private void showInvalidFormPlaceholders(String dangerMessage) {
        this.investedCard.setValue("—", null);
        this.maturityCard.setValue("—", null);
        this.netCard.setValue("—", null);
        this.buyingPowerCard.setValue(dangerMessage, Status.DANGER);
        this.chartCard.setVisible(false);
        this.projectionCard.setVisible(false);
    }
}
