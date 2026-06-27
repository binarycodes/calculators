package io.binarycodes.calculators.irr.ui;

import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.irr.domain.CashflowRow;

import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;

/**
 * The expanded cashflow schedule: every dated amount, signed, with the running
 * (undiscounted) total beside it. Recurring entries appear here as one row per
 * occurrence.
 */
public class CashflowGrid extends Grid<CashflowRow> {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

    private SupportedCurrency currency = SupportedCurrency.INR;

    public CashflowGrid() {
        addClassName("cashflow-grid");
        setAllRowsVisible(true);

        addColumn(row -> DATE_FORMAT.format(row.date()))
                .setHeader(Translations.get("grid.col.date")).setAutoWidth(true);
        addColumn(row -> row.description() == null ? "" : row.description())
                .setHeader(Translations.get("field.description")).setFlexGrow(1);
        addColumn(row -> MoneyFormatter.format(row.amount(), this.currency))
                .setHeader(Translations.get("grid.col.amount"))
                .setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        addColumn(row -> MoneyFormatter.format(row.cumulative(), this.currency))
                .setHeader(Translations.get("grid.col.cumulative"))
                .setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
    }

    public void update(List<CashflowRow> rows, SupportedCurrency currency) {
        this.currency = currency;
        setItems(rows);
    }
}
