package io.binarycodes.calculators.inflation.ui;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.Route;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.BaseCalculatorView;
import io.binarycodes.calculators.base.ui.SummaryCard;
import io.binarycodes.calculators.inflation.domain.InflationInputs;
import io.binarycodes.calculators.inflation.domain.InflationResult;
import io.binarycodes.calculators.inflation.service.InflationCalculator;
import io.binarycodes.calculators.inflation.service.InflationDefaultsProvider;
import io.binarycodes.calculators.inflation.service.InflationInputsStore;

import java.math.BigDecimal;

/**
 * The inflation-projection screen. {@link BaseCalculatorView} owns the header,
 * form, action row, and the persistence + share-link lifecycle; this class adds
 * the two summary cards (amount today / after the time period) and the chart.
 */
@Route("inflation")
@Menu(title = "Inflation Projection", icon = "vaadin:trending-up", order = 3)
public class InflationView extends BaseCalculatorView<InflationInputs, InflationCalculatorForm> {

    private final SummaryCard enteredCard = new SummaryCard(getTranslation("summary.inflation.amountToday"), VaadinIcon.MONEY.create());
    private final SummaryCard resultCard = new SummaryCard(getTranslation("summary.inflation.amountAfter"), VaadinIcon.TIME_FORWARD.create());
    private final InflationChart chart = new InflationChart();
    private final VerticalLayout chartCard;

    public InflationView(UserPreferences preferences,
                         InflationDefaultsProvider defaultsProvider,
                         InflationInputsStore inputsStore) {
        super(preferences, inputsStore, defaultsProvider,
                new InflationCalculatorForm(preferences), "inflation", "page.inflation");

        add(buildSummaryRow());
        this.chartCard = buildChartCard();
        add(this.chartCard);
    }

    @Override
    protected void updateResults() {
        if (!this.form.isValid()) {
            this.form.validate();
            this.form.showValidationMessages(null);
            this.enteredCard.setValue(getTranslation("common.dash"), null);
            this.resultCard.setValue(getTranslation("common.dash"), null);
            this.chartCard.setVisible(false);
            return;
        }

        final InflationInputs inputs = this.form.getInputs();
        final InflationResult result;
        try {
            result = InflationCalculator.calculate(inputs);
        } catch (final IllegalArgumentException invalid) {
            this.form.showValidationMessages(invalid.getMessage());
            this.enteredCard.setValue(getTranslation("common.dash"), null);
            this.resultCard.setValue(getTranslation("common.dash"), null);
            this.chartCard.setVisible(false);
            return;
        }
        this.form.showValidationMessages(null);

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
}
