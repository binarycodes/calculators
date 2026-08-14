package io.binarycodes.calculators.debt.ui;

import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.BaseGrid;
import io.binarycodes.calculators.debt.domain.DebtPayment;
import io.binarycodes.calculators.debt.domain.MonthlyPayment;

import java.math.BigDecimal;
import java.util.List;

/**
 * The month-by-month payment schedule for the chosen strategy: one column per
 * debt showing exactly what to pay into it that month, plus a total. Columns are
 * rebuilt from the plan's debts on each {@link #update}, so the column chooser
 * follows the current debt list. When the budget can't cover a debt's minimum,
 * the cell shows the shortfall in red beneath the payment; if any month has a
 * shortfall, a Total-shortfall column and a legend explaining the red appear.
 */
public class DebtMonthlyGrid extends BaseGrid<MonthlyPayment> {

    private final UserPreferences preferences;

    public DebtMonthlyGrid(UserPreferences preferences) {
        super(MonthlyPayment.class, false);
        this.preferences = preferences;
        addClassName("debt-monthly-grid");
        setHeight("420px");
        setWidthFull();
    }

    public void update(List<MonthlyPayment> months, SupportedCurrency currency) {
        clearTrackedColumns();
        clearRowLegend();
        if (months.isEmpty()) {
            setItems(List.of());
            return;
        }

        track(Translations.get("grid.col.debt.month"), addColumn(MonthlyPayment::month)
                .setHeader(Translations.get("grid.col.debt.month"))
                .setAutoWidth(true)
                .setFlexGrow(0)
                .setFrozen(true));

        final List<DebtPayment> firstRow = months.get(0).payments();
        for (int index = 0; index < firstRow.size(); index++) {
            final int debtIndex = index;
            final String debtName = firstRow.get(index).debtName();
            track(debtName, addComponentColumn(month -> paymentCell(month.payments().get(debtIndex), currency))
                    .setHeader(debtName)
                    .setAutoWidth(true)
                    .setTextAlign(ColumnTextAlign.END));
        }

        track(Translations.get("grid.col.debt.monthlyTotal"),
                addColumn(month -> MoneyFormatter.format(month.total(), currency))
                        .setHeader(Translations.get("grid.col.debt.monthlyTotal"))
                        .setAutoWidth(true)
                        .setTextAlign(ColumnTextAlign.END));

        // The shortfall total column and its legend only make sense once some
        // month actually falls short.
        if (months.stream().anyMatch(month -> month.totalShortfall().signum() > 0)) {
            track(Translations.get("grid.col.debt.shortfall"),
                    addColumn(month -> month.totalShortfall().signum() > 0
                            ? MoneyFormatter.format(month.totalShortfall(), currency)
                            : Translations.get("common.dash"))
                            .setHeader(Translations.get("grid.col.debt.shortfall"))
                            .setAutoWidth(true)
                            .setTextAlign(ColumnTextAlign.END));
            trackRowLegend("payment-shortfall", "debt.defaultLegendSwatch");
        }

        setItems(months);
    }

    private Div paymentCell(DebtPayment payment, SupportedCurrency currency) {
        final Div cell = new Div();
        cell.addClassName("payment-cell");
        cell.add(new Span(MoneyFormatter.format(payment.amount(), currency)));
        if (payment.shortfall().signum() > 0) {
            cell.add(shortfallLabel(payment.shortfall(), currency));
            cell.setTitle(Translations.get("debt.defaultTooltip"));
        }
        return cell;
    }

    private Span shortfallLabel(BigDecimal shortfall, SupportedCurrency currency) {
        final Span label = new Span(Translations.get("debt.shortfall", MoneyFormatter.format(shortfall, currency)));
        label.addClassName("payment-shortfall");
        return label;
    }
}
