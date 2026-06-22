package io.binarycodes.calculators.loan.ui;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import io.binarycodes.calculators.base.common.Status;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.BaseCalculatorView;
import io.binarycodes.calculators.base.ui.SummaryCard;
import io.binarycodes.calculators.loan.domain.LoanInputs;
import io.binarycodes.calculators.loan.domain.LoanResult;
import io.binarycodes.calculators.loan.service.LoanCalculator;
import io.binarycodes.calculators.loan.service.LoanDefaultsProvider;
import io.binarycodes.calculators.loan.service.LoanInputsStore;

/**
 * The loan / EMI screen. {@link BaseCalculatorView} owns the header, form, action
 * row, and the persistence + share-link lifecycle; this class adds the summary
 * cards (EMI / interest / payment, plus the reduce-tenure vs reduce-EMI
 * prepayment comparison and the real cost), the balance chart, and the
 * amortization grid.
 *
 * @see LoanCalculatorForm
 * @see LoanBalanceChart
 * @see LoanProjectionGrid
 */
@Route("loan")
@Menu(title = "Loan / EMI", icon = "vaadin:cash", order = 5)
@PageTitle("Loan / EMI Calculator")
public class LoanView extends BaseCalculatorView<LoanInputs, LoanCalculatorForm> {

    private final SummaryCard emiCard = new SummaryCard("Monthly EMI");
    private final SummaryCard totalInterestCard = new SummaryCard("Total Interest");
    private final SummaryCard totalPaymentCard = new SummaryCard("Total Payment");
    private final SummaryCard interestSavedCard = new SummaryCard("Interest Saved");
    private final SummaryCard lowerEmiCard = new SummaryCard("Lower EMI");
    private final SummaryCard realInterestCard = new SummaryCard("Interest (today's money)");

    private final LoanBalanceChart balanceChart = new LoanBalanceChart();
    private final LoanPaymentSplitChart paymentSplitChart = new LoanPaymentSplitChart();
    private final LoanProjectionGrid projectionGrid;
    private final RadioButtonGroup<ScheduleScenario> scheduleToggle = new RadioButtonGroup<>();

    private final VerticalLayout chartCard;
    private final VerticalLayout projectionCard;

    private LoanResult latestResult;

    /** Which amortization schedule the grid renders; selected via the header toggle. */
    private enum ScheduleScenario {
        REDUCE_TENURE("Reduce Tenure"),
        REDUCE_EMI("Reduce EMI");

        private final String label;

        ScheduleScenario(String label) {
            this.label = label;
        }
    }

    public LoanView(UserPreferences preferences,
                    LoanDefaultsProvider defaultsProvider,
                    LoanInputsStore inputsStore) {
        super(preferences, inputsStore, defaultsProvider,
                new LoanCalculatorForm(preferences), "loan", "Loan / EMI Calculator");
        this.projectionGrid = new LoanProjectionGrid(preferences);

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

        final LoanInputs inputs = this.form.getInputs();
        final LoanResult result;
        try {
            result = LoanCalculator.calculate(inputs);
        } catch (final IllegalArgumentException invalid) {
            this.form.showValidationMessages(invalid.getMessage());
            showInvalidFormPlaceholders();
            return;
        }
        this.form.showValidationMessages(null);

        final SupportedCurrency currency = this.preferences.currency();
        this.emiCard.setValue(MoneyFormatter.format(result.emi(), currency), null);
        this.totalInterestCard.setValue(MoneyFormatter.format(result.totalInterestBaseline(), currency), null);
        this.totalPaymentCard.setValue(MoneyFormatter.format(result.totalPaymentBaseline(), currency), null);

        if (result.hasPrepayments()) {
            this.interestSavedCard.setLabel("Interest Saved · Reduce Tenure");
            this.interestSavedCard.setValue(
                    MoneyFormatter.format(result.interestSavedTenure(), currency)
                            + "  (" + monthsSavedText(result.monthsSaved()) + ")",
                    Status.SUCCESS);

            this.lowerEmiCard.setLabel("Interest Saved · Reduce EMI");
            this.lowerEmiCard.setValue(MoneyFormatter.format(result.interestSavedEmi(), currency), Status.SUCCESS);
        } else {
            this.interestSavedCard.setLabel("Interest Saved");
            this.interestSavedCard.setValue("—", null);
            this.lowerEmiCard.setLabel("Interest Saved · Reduce EMI");
            this.lowerEmiCard.setValue("—", null);
        }

        this.realInterestCard.setValue(MoneyFormatter.format(result.realTotalInterest(), currency), null);

        this.chartCard.setVisible(true);
        this.projectionCard.setVisible(true);
        this.balanceChart.update(result, inputs.getLoanAmount(), currency);
        this.paymentSplitChart.update(result, currency);

        // Only reduce-tenure and reduce-EMI diverge under prepayments; without
        // them both schedules collapse onto the baseline, so the toggle is moot.
        this.latestResult = result;
        this.scheduleToggle.setVisible(result.hasPrepayments());
        renderSelectedSchedule();
    }

    private void renderSelectedSchedule() {
        if (this.latestResult == null) {
            return;
        }
        final boolean reduceEmi = this.scheduleToggle.isVisible()
                && this.scheduleToggle.getValue() == ScheduleScenario.REDUCE_EMI;
        this.projectionGrid.update(reduceEmi ? this.latestResult.reduceEmiRows() : this.latestResult.rows());
    }

    private HorizontalLayout buildSummaryRow() {
        final HorizontalLayout summaryRow = new HorizontalLayout(
                this.emiCard, this.totalInterestCard, this.totalPaymentCard,
                this.interestSavedCard, this.lowerEmiCard, this.realInterestCard);
        summaryRow.addClassName("summary-row");
        summaryRow.setWidthFull();
        summaryRow.setFlexGrow(1,
                this.emiCard, this.totalInterestCard, this.totalPaymentCard,
                this.interestSavedCard, this.lowerEmiCard, this.realInterestCard);
        summaryRow.setSpacing(true);
        return summaryRow;
    }

    private VerticalLayout buildChartCard() {
        final TabSheet charts = new TabSheet();
        charts.setWidthFull();
        charts.add("Outstanding Balance", this.balanceChart);
        charts.add("Principal vs Interest", this.paymentSplitChart);

        final VerticalLayout card = new VerticalLayout(charts);
        card.addClassName("chart-card");
        card.setPadding(false);
        card.setSpacing(false);
        card.setWidthFull();
        return card;
    }

    private VerticalLayout buildProjectionCard() {
        final H2 title = new H2("Amortization Schedule");

        this.scheduleToggle.setItems(ScheduleScenario.values());
        this.scheduleToggle.setItemLabelGenerator(scenario -> scenario.label);
        this.scheduleToggle.setValue(ScheduleScenario.REDUCE_TENURE);
        this.scheduleToggle.addClassNames("segmented-toggle", "schedule-toggle");
        this.scheduleToggle.setVisible(false);
        this.scheduleToggle.addValueChangeListener(event -> renderSelectedSchedule());

        final HorizontalLayout titleGroup = new HorizontalLayout(title, this.scheduleToggle);
        titleGroup.setAlignItems(FlexComponent.Alignment.CENTER);

        final HorizontalLayout header = new HorizontalLayout(titleGroup,
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
        this.emiCard.setValue("—", null);
        this.totalInterestCard.setValue("—", null);
        this.totalPaymentCard.setValue("—", null);
        this.interestSavedCard.setValue("—", null);
        this.lowerEmiCard.setValue("—", null);
        this.realInterestCard.setValue("—", null);
        this.chartCard.setVisible(false);
        this.projectionCard.setVisible(false);
    }

    private static String monthsSavedText(int months) {
        if (months <= 0) {
            return "no change";
        }
        final int years = months / 12;
        final int remainder = months % 12;
        final String span;
        if (years > 0 && remainder > 0) {
            span = years + "y " + remainder + "m";
        } else if (years > 0) {
            span = years + (years == 1 ? " yr" : " yrs");
        } else {
            span = remainder + " mo";
        }
        return span + " earlier";
    }
}
