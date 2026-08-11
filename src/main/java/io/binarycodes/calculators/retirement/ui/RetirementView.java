package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import io.binarycodes.calculators.base.common.Status;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.BaseCalculatorView;
import io.binarycodes.calculators.base.ui.SummaryCard;
import io.binarycodes.calculators.retirement.domain.ProjectionRow;
import io.binarycodes.calculators.retirement.domain.RetirementInputs;
import io.binarycodes.calculators.retirement.domain.RetirementResult;
import io.binarycodes.calculators.retirement.service.DefaultsProvider;
import io.binarycodes.calculators.retirement.service.RetirementCalculator;
import io.binarycodes.calculators.retirement.service.RetirementInputsStore;
import io.binarycodes.calculators.retirement.service.RetirementTimeline;

import java.math.BigDecimal;

/**
 * The retirement calculator screen. {@link BaseCalculatorView} owns the header,
 * form, action row, and the persistence + share-link lifecycle; this class adds
 * the summary cards, charts, and projection grid and renders them in
 * {@link #updateResults()}.
 *
 * @see CorpusChart
 * @see ExpensesChart
 * @see InvestmentsChart
 * @see ReturnOnInvestmentsChart
 * @see WithdrawalVsReturnsChart
 * @see RealCorpusChart
 * @see TimelineChart
 * @see ProjectionGrid
 */
@Route("retirement")
@AnonymousAllowed
@Menu(title = "Retirement Planner", icon = "vaadin:piggy-bank", order = 1)
public class RetirementView extends BaseCalculatorView<RetirementInputs, RetirementCalculatorForm> {

    private static final BigDecimal HEALTHY_CORPUS_MULTIPLIER = BigDecimal.valueOf(5);

    private final SummaryCard corpusAtRetirement   = new SummaryCard(getTranslation("summary.retirement.corpusAtRetirement"), VaadinIcon.WALLET.create());
    private final SummaryCard expensesAtRetirement = new SummaryCard(getTranslation("summary.retirement.annualExpenses"), VaadinIcon.CART.create());
    private final SummaryCard lastsUntil           = new SummaryCard(getTranslation("summary.retirement.lastsUntil"), VaadinIcon.HOURGLASS.create());
    private final SummaryCard finalCorpus          = new SummaryCard(getTranslation("summary.retirement.finalCorpus"), VaadinIcon.FLAG_CHECKERED.create());

    private final CorpusChart                corpusChart                = new CorpusChart();
    private final ExpensesChart              expensesChart              = new ExpensesChart();
    private final InvestmentsChart           investmentsChart           = new InvestmentsChart();
    private final ReturnOnInvestmentsChart   returnOnInvestmentsChart   = new ReturnOnInvestmentsChart();
    private final WithdrawalVsReturnsChart   withdrawalVsReturnsChart   = new WithdrawalVsReturnsChart();
    private final RealCorpusChart            realCorpusChart            = new RealCorpusChart();
    private final TimelineChart              timelineChart              = new TimelineChart();

    private final ProjectionGrid projectionGrid;

    public RetirementView(UserPreferences preferences,
                          DefaultsProvider defaultsProvider,
                          RetirementInputsStore inputsStore) {
        super(preferences, inputsStore, defaultsProvider,
                new RetirementCalculatorForm(preferences), "retirement", "page.retirement");
        this.projectionGrid = new ProjectionGrid(preferences);

        add(buildSummaryRow());
        add(buildChartsCard());
        add(buildProjectionGridCard());
    }

    @Override
    protected void updateResults() {
        if (!this.form.isValid()) {
            this.form.validate();
            this.form.showValidationMessages(null);
            showInvalidFormPlaceholders();
            return;
        }

        final RetirementInputs inputs = this.form.getInputs();

        final RetirementResult result;
        try {
            result = RetirementCalculator.calculate(inputs);
        } catch (final IllegalArgumentException invalid) {
            this.form.showValidationMessages(invalid.getMessage());
            showInvalidFormPlaceholders();
            return;
        }
        this.form.showValidationMessages(null);

        final SupportedCurrency currency = this.preferences.currency();
        updateRetirementYearSummaries(result, currency);
        updateLastsUntilSummary(result, inputs.getLifeExp());
        updateFinalCorpusSummary(result, currency);

        this.projectionGrid.update(result.rows());

        this.corpusChart.update(inputs, result, currency);
        this.expensesChart.update(result, currency);
        this.investmentsChart.update(result, currency);
        this.returnOnInvestmentsChart.update(inputs, result, currency);
        this.withdrawalVsReturnsChart.update(result, currency);
        this.realCorpusChart.update(inputs, result, currency);
        this.timelineChart.update(RetirementTimeline.build(inputs, result, currency), currency);
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
                new Tab(getTranslation("tab.retirement.chart.corpus")),
                new Tab(getTranslation("tab.retirement.chart.annualExpenses")),
                new Tab(getTranslation("tab.retirement.chart.investments")),
                new Tab(getTranslation("tab.retirement.chart.returnOnInvestments")),
                new Tab(getTranslation("tab.retirement.chart.withdrawalVsReturns")),
                new Tab(getTranslation("tab.retirement.chart.realCorpus")),
                new Tab(getTranslation("tab.retirement.chart.timeline")));

        final VerticalLayout activeChartContainer = new VerticalLayout();
        activeChartContainer.setPadding(false);
        activeChartContainer.setSpacing(false);
        activeChartContainer.setSizeFull();
        activeChartContainer.add(this.corpusChart);

        // Switch on tab index, not the label: labels are now translated, so
        // comparing display strings would break under any non-default locale.
        chartTabs.addSelectedChangeListener(event -> {
            activeChartContainer.removeAll();
            switch (chartTabs.getSelectedIndex()) {
                case 0 -> activeChartContainer.add(this.corpusChart);
                case 1 -> activeChartContainer.add(this.expensesChart);
                case 2 -> activeChartContainer.add(this.investmentsChart);
                case 3 -> activeChartContainer.add(this.returnOnInvestmentsChart);
                case 4 -> activeChartContainer.add(this.withdrawalVsReturnsChart);
                case 5 -> activeChartContainer.add(this.realCorpusChart);
                case 6 -> activeChartContainer.add(this.timelineChart);
                default -> activeChartContainer.add(this.corpusChart);
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
        final H2 title = new H2(getTranslation("section.projection"));
        final HorizontalLayout header = new HorizontalLayout(title, this.projectionGrid.createControls());
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        final VerticalLayout gridCard = new VerticalLayout(header, this.projectionGrid);
        gridCard.addClassName("grid-card");
        gridCard.setPadding(false);
        gridCard.setSpacing(true);
        gridCard.setWidthFull();
        return gridCard;
    }

    private void showInvalidFormPlaceholders() {
        this.corpusAtRetirement.setValue(getTranslation("common.dash"), null);
        this.expensesAtRetirement.setValue(getTranslation("common.dash"), null);
        this.lastsUntil.setValue(getTranslation("common.dash"), null);
        this.finalCorpus.setValue(getTranslation("common.dash"), null);
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
            this.lastsUntil.setLabel(getTranslation("summary.retirement.lastsUntil"));
            this.lastsUntil.setValue(getTranslation("retirement.lastsYears", lastFullyCoveredAge), Status.DANGER);
        } else {
            this.lastsUntil.setValue(getTranslation("retirement.lastsBeyond", lifeExpectancy), Status.SUCCESS);
        }
    }

    private void updateFinalCorpusSummary(RetirementResult result, SupportedCurrency currency) {
        final ProjectionRow lastsUntilRow = result.lastsUntilRow();
        final Status tone = finalCorpusTone(lastsUntilRow);
        this.finalCorpus.setLabel(result.corpusDepletedAt().isPresent()
                ? getTranslation("retirement.finalCorpusAtAge", lastsUntilRow.age())
                : getTranslation("retirement.finalCorpusAtLifeExp"));
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
