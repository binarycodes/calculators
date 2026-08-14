package io.binarycodes.calculators.debt.ui;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import io.binarycodes.calculators.base.common.Status;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.BaseCalculatorView;
import io.binarycodes.calculators.base.ui.SummaryCard;
import io.binarycodes.calculators.debt.domain.DebtPlanInputs;
import io.binarycodes.calculators.debt.domain.DebtPlanResult;
import io.binarycodes.calculators.debt.domain.PayoffStrategy;
import io.binarycodes.calculators.debt.service.DebtCalculator;
import io.binarycodes.calculators.debt.service.DebtDefaultsProvider;
import io.binarycodes.calculators.debt.service.DebtInputsStore;

import java.math.BigDecimal;

/**
 * The debt-payoff planner screen. {@link BaseCalculatorView} owns the header,
 * form, action row, and the persistence + share-link lifecycle; this class adds
 * five summary cards (debt-free horizon, total interest with a today's-money
 * subtitle, interest and time saved versus paying minimums only, and the delta
 * against the other strategy), the three-way balance comparison chart, and the
 * year-by-year projection grid for the chosen strategy.
 *
 * @see DebtCalculatorForm
 * @see DebtComparisonChart
 * @see DebtProjectionGrid
 */
@Route("debt")
@AnonymousAllowed
@Menu(title = "Debt Planner", icon = "vaadin:credit-card", order = 8)
public class DebtView extends BaseCalculatorView<DebtPlanInputs, DebtCalculatorForm> {

    private final SummaryCard debtFreeCard = new SummaryCard(getTranslation("summary.debt.debtFree"), VaadinIcon.CHECK_CIRCLE.create());
    private final SummaryCard totalInterestCard = new SummaryCard(getTranslation("summary.debt.totalInterest"), VaadinIcon.MONEY.create());
    private final SummaryCard interestSavedCard = new SummaryCard(getTranslation("summary.debt.interestSaved"), VaadinIcon.PIGGY_BANK.create());
    private final SummaryCard timeSavedCard = new SummaryCard(getTranslation("summary.debt.timeSaved"), VaadinIcon.CLOCK.create());
    private final SummaryCard vsAlternativeCard = new SummaryCard(getTranslation("summary.debt.vsAlternative"), VaadinIcon.EXCHANGE.create());

    private final DebtComparisonChart comparisonChart = new DebtComparisonChart();
    private final DebtProjectionGrid projectionGrid;
    private final DebtDefaultsProvider defaultsProvider;

    private final VerticalLayout chartCard;
    private final VerticalLayout projectionCard;

    public DebtView(UserPreferences preferences,
                    DebtDefaultsProvider defaultsProvider,
                    DebtInputsStore inputsStore) {
        super(preferences, inputsStore, defaultsProvider,
                new DebtCalculatorForm(preferences), "debt", "page.debt");
        this.defaultsProvider = defaultsProvider;
        this.projectionGrid = new DebtProjectionGrid(preferences);

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

        final DebtPlanInputs inputs = this.form.getInputs();
        final SupportedCurrency currency = this.preferences.currency();
        final DebtPlanResult result;
        try {
            result = DebtCalculator.calculate(inputs, this.defaultsProvider.minimumFloor(currency));
        } catch (final IllegalArgumentException invalid) {
            this.form.showValidationMessages(getTranslation(invalid.getMessage()));
            showInvalidFormPlaceholders();
            return;
        }
        this.form.showValidationMessages(null);

        this.debtFreeCard.setValue(duration(result.primary().payoffMonth()), Status.SUCCESS);

        this.totalInterestCard.setValue(MoneyFormatter.format(result.primary().totalInterest(), currency), null);
        this.totalInterestCard.setSecondaryText(getTranslation("summary.debt.todaysMoney",
                MoneyFormatter.format(result.primary().realTotalInterest(), currency)));

        this.interestSavedCard.setValue(MoneyFormatter.format(result.interestSaved(), currency),
                result.interestSaved().signum() > 0 ? Status.SUCCESS : null);
        this.timeSavedCard.setValue(duration(result.monthsSaved()),
                result.monthsSaved() > 0 ? Status.SUCCESS : null);

        updateAlternativeCard(result, currency);

        this.chartCard.setVisible(true);
        this.projectionCard.setVisible(true);
        this.comparisonChart.update(result, currency);
        this.projectionGrid.update(result.primary().years(), payoffYear(result.primary().payoffMonth()));
    }

    private void updateAlternativeCard(DebtPlanResult result, SupportedCurrency currency) {
        this.vsAlternativeCard.setLabel(getTranslation("summary.debt.vsAlternative.named",
                strategyName(result.alternativeStrategy())));

        final BigDecimal difference = result.alternative().totalInterest()
                .subtract(result.primary().totalInterest());
        if (difference.signum() > 0) {
            this.vsAlternativeCard.setValue(
                    getTranslation("debt.lessInterest", MoneyFormatter.format(difference, currency)), Status.SUCCESS);
        } else if (difference.signum() < 0) {
            this.vsAlternativeCard.setValue(
                    getTranslation("debt.moreInterest", MoneyFormatter.format(difference.abs(), currency)), Status.WARNING);
        } else {
            this.vsAlternativeCard.setValue(getTranslation("debt.sameInterest"), null);
        }
    }

    private String strategyName(PayoffStrategy strategy) {
        return strategy == PayoffStrategy.SNOWBALL
                ? getTranslation("debt.strategy.snowball")
                : getTranslation("debt.strategy.avalanche");
    }

    /** Months rendered as a human duration: "7 months", "2 years", or "2 years 7 months". */
    private String duration(int months) {
        final int years = months / 12;
        final int remainingMonths = months % 12;
        if (years == 0) {
            return getTranslation("debt.duration.months", remainingMonths);
        }
        if (remainingMonths == 0) {
            return getTranslation("debt.duration.years", years);
        }
        return getTranslation("debt.duration.yearsMonths", years, remainingMonths);
    }

    private static int payoffYear(int payoffMonth) {
        return (payoffMonth + 11) / 12;
    }

    private HorizontalLayout buildSummaryRow() {
        final HorizontalLayout summaryRow = new HorizontalLayout(
                this.debtFreeCard, this.totalInterestCard, this.interestSavedCard,
                this.timeSavedCard, this.vsAlternativeCard);
        summaryRow.addClassName("summary-row");
        summaryRow.setWidthFull();
        summaryRow.setFlexGrow(1, this.debtFreeCard, this.totalInterestCard, this.interestSavedCard,
                this.timeSavedCard, this.vsAlternativeCard);
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
        final H2 title = new H2(getTranslation("section.projectionByYear"));
        final HorizontalLayout header = new HorizontalLayout(title, this.projectionGrid.createControls());
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
        final String dash = getTranslation("common.dash");
        this.debtFreeCard.setValue(dash, null);
        this.totalInterestCard.setValue(dash, null);
        this.totalInterestCard.setSecondaryText(null);
        this.interestSavedCard.setValue(dash, null);
        this.timeSavedCard.setValue(dash, null);
        this.vsAlternativeCard.setValue(dash, null);
        this.chartCard.setVisible(false);
        this.projectionCard.setVisible(false);
    }
}
