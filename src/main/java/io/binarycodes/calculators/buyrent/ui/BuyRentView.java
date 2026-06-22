package io.binarycodes.calculators.buyrent.ui;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import io.binarycodes.calculators.base.common.Status;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.BaseCalculatorView;
import io.binarycodes.calculators.base.ui.SummaryCard;
import io.binarycodes.calculators.buyrent.domain.BuyRentInputs;
import io.binarycodes.calculators.buyrent.domain.BuyRentResult;
import io.binarycodes.calculators.buyrent.service.BuyRentCalculator;
import io.binarycodes.calculators.buyrent.service.BuyRentDefaultsProvider;
import io.binarycodes.calculators.buyrent.service.BuyRentInputsStore;

/**
 * The buy-vs-rent screen. {@link BaseCalculatorView} owns the header, form,
 * action row, and the persistence + share-link lifecycle; this class adds five
 * summary cards (monthly costs, break-even year, and net worths at horizon),
 * the net-worth comparison chart, and the year-by-year projection grid.
 *
 * @see BuyRentCalculatorForm
 * @see BuyRentComparisonChart
 * @see BuyRentProjectionGrid
 */
@Route("buyrent")
@Menu(title = "Buy vs Rent", icon = "vaadin:home", order = 6)
@PageTitle("Buy vs Rent Calculator")
public class BuyRentView extends BaseCalculatorView<BuyRentInputs, BuyRentCalculatorForm> {

    private final SummaryCard monthlyCostBuyCard = new SummaryCard("Monthly Cost: Buy");
    private final SummaryCard monthlyCostRentCard = new SummaryCard("Monthly Cost: Rent");
    private final SummaryCard breakEvenCard = new SummaryCard("Break-Even");
    private final SummaryCard netWorthBuyCard = new SummaryCard("Net Worth: Buy");
    private final SummaryCard netWorthRentCard = new SummaryCard("Net Worth: Rent");

    private final BuyRentComparisonChart comparisonChart = new BuyRentComparisonChart();
    private final BuyRentProjectionGrid projectionGrid;

    private final VerticalLayout chartCard;
    private final VerticalLayout projectionCard;

    public BuyRentView(UserPreferences preferences,
                       BuyRentDefaultsProvider defaultsProvider,
                       BuyRentInputsStore inputsStore) {
        super(preferences, inputsStore, defaultsProvider,
                new BuyRentCalculatorForm(preferences), "buyrent", "Buy vs Rent Calculator");
        this.projectionGrid = new BuyRentProjectionGrid(preferences);

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

        final BuyRentInputs inputs = this.form.getInputs();
        final BuyRentResult result;
        try {
            result = BuyRentCalculator.calculate(inputs);
        } catch (final IllegalArgumentException invalid) {
            this.form.showValidationMessages(invalid.getMessage());
            showInvalidFormPlaceholders();
            return;
        }
        this.form.showValidationMessages(null);

        final SupportedCurrency currency = this.preferences.currency();

        this.monthlyCostBuyCard.setValue(MoneyFormatter.format(result.initialMonthlyCostBuy(), currency), null);
        this.monthlyCostRentCard.setValue(MoneyFormatter.format(result.initialMonthlyCostRent(), currency), null);

        if (result.breakEvenYear() > 0) {
            final boolean buyWins = result.equityAtHorizon()
                    .compareTo(result.rentPortfolioAtHorizon()) >= 0;
            this.breakEvenCard.setValue("Year " + result.breakEvenYear(),
                    buyWins ? Status.SUCCESS : null);
        } else {
            this.breakEvenCard.setValue("Not in horizon", Status.WARNING);
        }

        final boolean buyAheadAtHorizon = result.equityAtHorizon()
                .compareTo(result.rentPortfolioAtHorizon()) >= 0;
        this.netWorthBuyCard.setValue(
                MoneyFormatter.format(result.equityAtHorizon(), currency),
                buyAheadAtHorizon ? Status.SUCCESS : null);
        this.netWorthRentCard.setValue(
                MoneyFormatter.format(result.rentPortfolioAtHorizon(), currency),
                buyAheadAtHorizon ? null : Status.SUCCESS);

        this.chartCard.setVisible(true);
        this.projectionCard.setVisible(true);
        this.comparisonChart.update(result, currency);
        this.projectionGrid.update(result.rows(), result.breakEvenYear());
    }

    private HorizontalLayout buildSummaryRow() {
        final HorizontalLayout summaryRow = new HorizontalLayout(
                this.monthlyCostBuyCard, this.monthlyCostRentCard, this.breakEvenCard,
                this.netWorthBuyCard, this.netWorthRentCard);
        summaryRow.addClassName("summary-row");
        summaryRow.setWidthFull();
        summaryRow.setFlexGrow(1,
                this.monthlyCostBuyCard, this.monthlyCostRentCard, this.breakEvenCard,
                this.netWorthBuyCard, this.netWorthRentCard);
        summaryRow.setSpacing(true);
        return summaryRow;
    }

    private VerticalLayout buildChartCard() {
        final VerticalLayout card = new VerticalLayout(this.comparisonChart);
        card.addClassName("chart-card");
        card.setPadding(false);
        card.setSpacing(false);
        card.setWidthFull();
        return card;
    }

    private VerticalLayout buildProjectionCard() {
        final H2 title = new H2("Year-by-Year Projection");

        final VerticalLayout card = new VerticalLayout(title, this.projectionGrid);
        card.addClassName("grid-card");
        card.setPadding(false);
        card.setSpacing(true);
        card.setWidthFull();
        return card;
    }

    private void showInvalidFormPlaceholders() {
        this.monthlyCostBuyCard.setValue("—", null);
        this.monthlyCostRentCard.setValue("—", null);
        this.breakEvenCard.setValue("—", null);
        this.netWorthBuyCard.setValue("—", null);
        this.netWorthRentCard.setValue("—", null);
        this.chartCard.setVisible(false);
        this.projectionCard.setVisible(false);
    }
}
