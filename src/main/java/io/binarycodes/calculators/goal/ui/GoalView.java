package io.binarycodes.calculators.goal.ui;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
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
import io.binarycodes.calculators.goal.domain.GoalInputs;
import io.binarycodes.calculators.goal.domain.GoalResult;
import io.binarycodes.calculators.goal.service.GoalCalculator;
import io.binarycodes.calculators.goal.service.GoalDefaultsProvider;
import io.binarycodes.calculators.goal.service.GoalInputsStore;

/**
 * The goal-planner screen. {@link BaseCalculatorView} owns the header, form,
 * action row, and the persistence + share-link lifecycle; this class adds the
 * summary cards, growth chart, and projection grid.
 *
 * @see GoalCalculatorForm
 * @see GoalGrowthChart
 * @see GoalProjectionGrid
 */
@Route("goal")
@Menu(title = "Goal Planner", icon = "vaadin:bullseye", order = 2)
@PageTitle("Goal Planner")
public class GoalView extends BaseCalculatorView<GoalInputs, GoalCalculatorForm> {

    private final SummaryCard monthlyInvestment   = new SummaryCard("Monthly Investment", VaadinIcon.CALENDAR.create());
    private final SummaryCard yearlyInvestment    = new SummaryCard("First-Year Investment", VaadinIcon.COIN_PILES.create());
    private final SummaryCard finalCorpus         = new SummaryCard("Final Corpus (gross)", VaadinIcon.FLAG_CHECKERED.create());
    private final SummaryCard taxAtExit           = new SummaryCard("Tax at Exit", VaadinIcon.INVOICE.create());

    private final GoalGrowthChart    growthChart    = new GoalGrowthChart();
    private final GoalProjectionGrid projectionGrid;

    private final VerticalLayout chartCard;
    private final VerticalLayout projectionCard;

    public GoalView(UserPreferences preferences,
                    GoalDefaultsProvider defaultsProvider,
                    GoalInputsStore inputsStore) {
        super(preferences, inputsStore, defaultsProvider,
                new GoalCalculatorForm(preferences), "goal", "Goal Planner");
        this.projectionGrid = new GoalProjectionGrid(preferences);

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

        final GoalInputs inputs = this.form.getInputs();
        final GoalResult result;
        try {
            result = GoalCalculator.calculate(inputs);
        } catch (final IllegalArgumentException invalid) {
            this.form.showValidationMessages(invalid.getMessage());
            showInvalidFormPlaceholders();
            return;
        }
        this.form.showValidationMessages(null);

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
        this.monthlyInvestment.setValue("—", null);
        this.yearlyInvestment.setValue("—", null);
        this.finalCorpus.setValue("—", null);
        this.taxAtExit.setValue("—", null);
        this.form.setInflationHelperText("");
        this.chartCard.setVisible(false);
        this.projectionCard.setVisible(false);
    }
}
