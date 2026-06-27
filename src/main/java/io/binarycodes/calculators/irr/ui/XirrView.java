package io.binarycodes.calculators.irr.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.Route;
import io.binarycodes.calculators.base.common.Status;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.BaseCalculatorView;
import io.binarycodes.calculators.base.ui.SummaryCard;
import io.binarycodes.calculators.irr.domain.XirrInputs;
import io.binarycodes.calculators.irr.domain.XirrResult;
import io.binarycodes.calculators.irr.domain.XirrStatus;
import io.binarycodes.calculators.irr.service.XirrCalculator;
import io.binarycodes.calculators.irr.service.XirrDefaultsProvider;
import io.binarycodes.calculators.irr.service.XirrInputsStore;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The IRR/XIRR calculator screen. {@link BaseCalculatorView} owns the header,
 * form, action row, and the persistence + share-link lifecycle; this class adds
 * the summary cards, the non-unique-rate warning banner, the three analysis
 * charts, and the cashflow schedule grid, and renders them in
 * {@link #updateResults()}.
 *
 * @see CashflowTimelineChart
 * @see NpvVsRateChart
 * @see CumulativeCashflowChart
 * @see CashflowGrid
 */
@Route("xirr")
@Menu(title = "IRR / XIRR", icon = "vaadin:chart-line", order = 7)
public class XirrView extends BaseCalculatorView<XirrInputs, XirrCalculatorForm> {

    private final SummaryCard rateCard = new SummaryCard(getTranslation("summary.xirr.rate"), VaadinIcon.TRENDING_UP.create());
    private final SummaryCard investedCard = new SummaryCard(getTranslation("summary.xirr.invested"), VaadinIcon.MONEY_DEPOSIT.create());
    private final SummaryCard withdrawnCard = new SummaryCard(getTranslation("summary.xirr.withdrawn"), VaadinIcon.MONEY_WITHDRAW.create());
    private final SummaryCard netCard = new SummaryCard(getTranslation("summary.xirr.net"), VaadinIcon.SCALE.create());

    private final Div warningBanner = new Div();

    private final CashflowTimelineChart timelineChart = new CashflowTimelineChart();
    private final NpvVsRateChart npvChart = new NpvVsRateChart();
    private final CumulativeCashflowChart cumulativeChart = new CumulativeCashflowChart();

    private final CashflowGrid cashflowGrid = new CashflowGrid();

    private final VerticalLayout chartsCard;
    private final VerticalLayout gridCard;

    public XirrView(UserPreferences preferences,
                    XirrDefaultsProvider defaultsProvider,
                    XirrInputsStore inputsStore) {
        super(preferences, inputsStore, defaultsProvider,
                new XirrCalculatorForm(preferences), "xirr", "page.xirr");

        this.warningBanner.addClassName("xirr-banner");
        this.warningBanner.setVisible(false);

        add(buildSummaryRow());
        add(this.warningBanner);
        this.chartsCard = buildChartsCard();
        add(this.chartsCard);
        this.gridCard = buildGridCard();
        add(this.gridCard);
    }

    @Override
    protected void updateResults() {
        if (!this.form.isValid()) {
            this.form.showValidationMessages(null);
            showInvalidFormPlaceholders();
            hideBanner();
            return;
        }

        final XirrInputs inputs = this.form.getInputs();
        final XirrResult result;
        try {
            result = XirrCalculator.calculate(inputs);
        } catch (final IllegalArgumentException invalid) {
            this.form.showValidationMessages(null);
            showInvalidFormPlaceholders();
            showError(getTranslation(invalid.getMessage()));
            return;
        }
        this.form.showValidationMessages(null);

        final SupportedCurrency currency = this.preferences.currency();
        final boolean nonUnique = result.status() == XirrStatus.NON_UNIQUE;

        this.rateCard.setValue(formatPercent(result.xirr()), nonUnique ? Status.WARNING : null);
        this.investedCard.setValue(MoneyFormatter.format(result.totalInvested(), currency), null);
        this.withdrawnCard.setValue(MoneyFormatter.format(result.totalWithdrawn(), currency), null);
        this.netCard.setValue(MoneyFormatter.format(result.netCashflow(), currency),
                result.netCashflow().signum() >= 0 ? Status.SUCCESS : Status.DANGER);

        if (nonUnique) {
            showWarning(getTranslation("irr.warning.multipleRoots",
                    result.signChanges(), result.roots().size(), formatRoots(result.roots())));
        } else {
            hideBanner();
        }

        this.chartsCard.setVisible(true);
        this.gridCard.setVisible(true);
        this.timelineChart.update(result.cashflows(), currency);
        this.npvChart.update(result.npvCurve(), result.roots(), currency);
        this.cumulativeChart.update(result.cashflows(), result.paybackDate(), currency);
        this.cashflowGrid.update(result.cashflows(), currency);
    }

    private HorizontalLayout buildSummaryRow() {
        final HorizontalLayout summaryRow = new HorizontalLayout(
                this.rateCard, this.investedCard, this.withdrawnCard, this.netCard);
        summaryRow.addClassName("summary-row");
        summaryRow.setWidthFull();
        summaryRow.setFlexGrow(1, this.rateCard, this.investedCard, this.withdrawnCard, this.netCard);
        summaryRow.setSpacing(true);
        return summaryRow;
    }

    private VerticalLayout buildChartsCard() {
        final Tabs chartTabs = new Tabs(
                new Tab(getTranslation("tab.xirr.chart.timeline")),
                new Tab(getTranslation("tab.xirr.chart.npv")),
                new Tab(getTranslation("tab.xirr.chart.cumulative")));

        final VerticalLayout activeChartContainer = new VerticalLayout();
        activeChartContainer.setPadding(false);
        activeChartContainer.setSpacing(false);
        activeChartContainer.setSizeFull();
        activeChartContainer.add(this.timelineChart);

        chartTabs.addSelectedChangeListener(event -> {
            activeChartContainer.removeAll();
            switch (chartTabs.getSelectedIndex()) {
                case 1 -> activeChartContainer.add(this.npvChart);
                case 2 -> activeChartContainer.add(this.cumulativeChart);
                default -> activeChartContainer.add(this.timelineChart);
            }
        });

        final VerticalLayout card = new VerticalLayout(chartTabs, activeChartContainer);
        card.addClassName("chart-card");
        card.setPadding(false);
        card.setSpacing(false);
        card.setWidthFull();
        return card;
    }

    private VerticalLayout buildGridCard() {
        final H2 title = new H2(getTranslation("section.xirr.schedule"));
        final HorizontalLayout header = new HorizontalLayout(title, this.cashflowGrid.createColumnChooser());
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        final VerticalLayout card = new VerticalLayout(header, this.cashflowGrid);
        card.addClassName("grid-card");
        card.setPadding(false);
        card.setSpacing(true);
        card.setWidthFull();
        return card;
    }

    private void showInvalidFormPlaceholders() {
        final String dash = getTranslation("common.dash");
        this.rateCard.setValue(dash, null);
        this.investedCard.setValue(dash, null);
        this.withdrawnCard.setValue(dash, null);
        this.netCard.setValue(dash, null);
        this.chartsCard.setVisible(false);
        this.gridCard.setVisible(false);
    }

    private void showWarning(String message) {
        this.warningBanner.setText(message);
        this.warningBanner.getElement().getClassList().set("error", false);
        this.warningBanner.getElement().getClassList().set("warning", true);
        this.warningBanner.setVisible(true);
    }

    private void showError(String message) {
        this.warningBanner.setText(message);
        this.warningBanner.getElement().getClassList().set("warning", false);
        this.warningBanner.getElement().getClassList().set("error", true);
        this.warningBanner.setVisible(true);
    }

    private void hideBanner() {
        this.warningBanner.setVisible(false);
    }

    private static String formatPercent(BigDecimal fraction) {
        return fraction.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static String formatRoots(List<BigDecimal> roots) {
        return roots.stream().map(XirrView::formatPercent).collect(Collectors.joining(", "));
    }
}
