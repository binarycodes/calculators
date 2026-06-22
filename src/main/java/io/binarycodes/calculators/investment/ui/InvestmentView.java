package io.binarycodes.calculators.investment.ui;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.BaseCalculatorView;
import io.binarycodes.calculators.base.ui.SummaryCard;
import io.binarycodes.calculators.investment.domain.InvestmentInputs;
import io.binarycodes.calculators.investment.domain.InvestmentResult;
import io.binarycodes.calculators.investment.service.InvestmentCalculator;
import io.binarycodes.calculators.investment.service.InvestmentDefaultsProvider;
import io.binarycodes.calculators.investment.service.InvestmentInputsStore;

/**
 * The investment-calculator screen. {@link BaseCalculatorView} owns the header,
 * form, action row, and the persistence + share-link lifecycle; this class adds
 * the four summary cards, the corpus build-up chart, and the projection grid.
 */
@Route("investment")
@Menu(title = "Investment", icon = "vaadin:coin-piles", order = 4)
@PageTitle("Investment Calculator")
public class InvestmentView extends BaseCalculatorView<InvestmentInputs, InvestmentCalculatorForm> {

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
        super(preferences, inputsStore, defaultsProvider,
                new InvestmentCalculatorForm(preferences), "investment", "Investment Calculator");
        this.projectionGrid = new InvestmentProjectionGrid(preferences);

        add(buildSummaryRow());
        this.chartCard = buildChartCard();
        add(this.chartCard);
        this.projectionCard = buildProjectionCard();
        add(this.projectionCard);
    }

    @Override
    protected void updateResults() {
        if (!this.form.isValid()) {
            this.form.validate();
            this.form.showValidationMessages(null);
            showInvalidFormPlaceholders();
            return;
        }

        final InvestmentInputs inputs = this.form.getInputs();
        final InvestmentResult result;
        try {
            result = InvestmentCalculator.calculate(inputs);
        } catch (final IllegalArgumentException invalid) {
            this.form.showValidationMessages(invalid.getMessage());
            showInvalidFormPlaceholders();
            return;
        }
        this.form.showValidationMessages(null);

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

    private void showInvalidFormPlaceholders() {
        this.investedCard.setValue("—", null);
        this.maturityCard.setValue("—", null);
        this.netCard.setValue("—", null);
        this.buyingPowerCard.setValue("—", null);
        this.chartCard.setVisible(false);
        this.projectionCard.setVisible(false);
    }
}
