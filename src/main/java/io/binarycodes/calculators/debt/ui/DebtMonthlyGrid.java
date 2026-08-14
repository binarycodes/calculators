package io.binarycodes.calculators.debt.ui;

import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.debt.domain.DebtPayment;
import io.binarycodes.calculators.debt.domain.MonthlyPayment;

import java.util.List;

/**
 * The month-by-month payment schedule for the chosen strategy: one column per
 * debt showing exactly what to pay into it that month, plus a total. Columns are
 * built from the plan's debts, so they change with the debt list. A cell is
 * flagged (danger colour) in any month the budget couldn't cover that debt's
 * minimum — a default — and a legend explains it when one occurs.
 */
public class DebtMonthlyGrid extends Grid<MonthlyPayment> {

    private final UserPreferences preferences;

    public DebtMonthlyGrid(UserPreferences preferences) {
        super(MonthlyPayment.class, false);
        this.preferences = preferences;
        addClassName("debt-monthly-grid");
        setHeight("420px");
        setWidthFull();
    }

    public void update(List<MonthlyPayment> months, SupportedCurrency currency) {
        removeAllColumns();
        if (months.isEmpty()) {
            setItems(List.of());
            return;
        }

        addColumn(MonthlyPayment::month)
                .setHeader(Translations.get("grid.col.debt.month"))
                .setAutoWidth(true)
                .setFlexGrow(0)
                .setFrozen(true);

        final List<DebtPayment> firstRow = months.get(0).payments();
        for (int index = 0; index < firstRow.size(); index++) {
            final int debtIndex = index;
            addComponentColumn(month -> paymentCell(month.payments().get(debtIndex), currency))
                    .setHeader(firstRow.get(index).debtName())
                    .setAutoWidth(true)
                    .setTextAlign(ColumnTextAlign.END);
        }

        addColumn(month -> MoneyFormatter.format(month.total(), currency))
                .setHeader(Translations.get("grid.col.debt.monthlyTotal"))
                .setAutoWidth(true)
                .setTextAlign(ColumnTextAlign.END);

        setItems(months);
    }

    private Span paymentCell(DebtPayment payment, SupportedCurrency currency) {
        final Span cell = new Span(MoneyFormatter.format(payment.amount(), currency));
        if (payment.defaulted()) {
            cell.addClassName("payment-default");
            cell.setTitle(Translations.get("debt.defaultTooltip"));
        }
        return cell;
    }
}
