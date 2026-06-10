package io.binarycodes.calculators.inflation.ui;

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
import io.binarycodes.calculators.inflation.domain.InflationInputs;
import io.binarycodes.calculators.inflation.domain.InflationResult;
import io.binarycodes.calculators.inflation.service.InflationCalculator;
import io.binarycodes.calculators.inflation.service.InflationDefaultsProvider;
import io.binarycodes.calculators.inflation.service.InflationInputsStore;

import java.math.BigDecimal;

/**
 * The inflation-projection screen. A single input form drives two summary
 * cards: the amount entered and its value at the other end of the horizon
 * (inflated forward, or discounted back to today's purchasing power).
 */
@Route("inflation")
@Menu(title = "Inflation Projection", icon = "vaadin:trending-up", order = 3)
@PageTitle("Inflation Projection")
public class InflationView extends VerticalLayout {

    private final UserPreferences preferences;
    private final InflationDefaultsProvider defaultsProvider;
    private final InflationInputsStore inputsStore;

    private final InflationCalculatorForm form;

    private final SummaryCard enteredCard = new SummaryCard("Amount Today");
    private final SummaryCard resultCard = new SummaryCard("Amount After Time Period");
    private final InflationChart chart = new InflationChart();
    private final VerticalLayout chartCard;

    public InflationView(UserPreferences preferences,
                         InflationDefaultsProvider defaultsProvider,
                         InflationInputsStore inputsStore) {
        this.preferences = preferences;
        this.defaultsProvider = defaultsProvider;
        this.inputsStore = inputsStore;

        addClassName("inflation-view");
        setWidthFull();
        setPadding(true);
        setSpacing(true);

        this.form = new InflationCalculatorForm(preferences);
        Signal.effect(this, context -> {
            this.form.inputsSignal().get();
            if (context.isInitialRun()) {
                return;
            }
            onInputChanged();
        });

        add(new H2("Inflation Projection"));
        add(this.form);
        add(buildActionRow());
        add(buildSummaryRow());
        this.chartCard = buildChartCard();
        add(this.chartCard);

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
        final HorizontalLayout summaryRow = new HorizontalLayout(this.enteredCard, this.resultCard);
        summaryRow.addClassName("summary-row");
        summaryRow.setWidthFull();
        summaryRow.setFlexGrow(1, this.enteredCard, this.resultCard);
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

    private void onInputChanged() {
        this.inputsStore.save(this.preferences.currency(), this.form.getInputs());
        recalculate();
    }

    private void onPreferencesChanged() {
        populateFormFromPersistedOrDefault(this.preferences.currency());
        recalculate();
    }

    private void populateFormFromPersistedOrDefault(SupportedCurrency currency) {
        InflationInputs inputs = this.inputsStore.get(currency);
        if (inputs == null) {
            inputs = this.defaultsProvider.forCurrency(currency);
        }
        this.form.setInputs(inputs);
    }

    private void resetToDefaults() {
        final SupportedCurrency currency = this.preferences.currency();
        final InflationInputs defaultInputs = this.defaultsProvider.forCurrency(currency);
        this.inputsStore.save(currency, defaultInputs);
        this.form.setInputs(defaultInputs);
        recalculate();
    }

    private void recalculate() {
        if (!this.form.isValid()) {
            this.form.validate();
            this.enteredCard.setValue("—", null);
            this.resultCard.setValue("Fix the highlighted fields to recalculate.", Status.DANGER);
            this.chartCard.setVisible(false);
            return;
        }

        final InflationInputs inputs = this.form.getInputs();
        final InflationResult result;
        try {
            result = InflationCalculator.calculate(inputs);
        } catch (final IllegalArgumentException invalid) {
            this.enteredCard.setValue("—", null);
            this.resultCard.setValue(invalid.getMessage(), Status.DANGER);
            this.chartCard.setVisible(false);
            return;
        }

        final SupportedCurrency currency = this.preferences.currency();
        // Left card is always today's money, right card is always the value
        // after the time period — positioning is fixed regardless of direction.
        final boolean forward = result.amountIsToday();
        final BigDecimal todayAmount = forward ? result.inputAmount() : result.resultAmount();
        final BigDecimal afterAmount = forward ? result.resultAmount() : result.inputAmount();

        this.enteredCard.setValue(MoneyFormatter.format(todayAmount, currency), null);
        this.resultCard.setValue(MoneyFormatter.format(afterAmount, currency), null);

        this.chartCard.setVisible(true);
        this.chart.update(result, currency);
    }
}
