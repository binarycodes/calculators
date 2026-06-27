package io.binarycodes.calculators.irr.ui;

import com.vaadin.flow.component.grid.ColumnTextAlign;
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.base.ui.ColumnChooserGrid;
import io.binarycodes.calculators.irr.domain.CashflowRow;

import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;

/**
 * The expanded cashflow schedule: every dated amount, signed, with the running
 * (undiscounted) total beside it. Recurring entries appear here as one row per
 * occurrence. {@link #createColumnChooser()} returns a cog-menu the parent view
 * places beside the grid header to toggle columns.
 */
public class CashflowGrid extends ColumnChooserGrid<CashflowRow> {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

    private SupportedCurrency currency = SupportedCurrency.INR;

    public CashflowGrid() {
        super(CashflowRow.class, false);
        addClassName("cashflow-grid");
        setAllRowsVisible(true);
        setWidthFull();

        track(Translations.get("grid.col.date"), addColumn(row -> DATE_FORMAT.format(row.date()))
                .setHeader(Translations.get("grid.col.date")).setAutoWidth(true));
        track(Translations.get("field.description"), addColumn(row -> row.description() == null ? "" : row.description())
                .setHeader(Translations.get("field.description")).setFlexGrow(1));
        track(Translations.get("grid.col.amount"), addColumn(row -> MoneyFormatter.format(row.amount(), this.currency))
                .setHeader(Translations.get("grid.col.amount"))
                .setTextAlign(ColumnTextAlign.END).setAutoWidth(true));
        track(Translations.get("grid.col.cumulative"), addColumn(row -> MoneyFormatter.format(row.cumulative(), this.currency))
                .setHeader(Translations.get("grid.col.cumulative"))
                .setTextAlign(ColumnTextAlign.END).setAutoWidth(true));
    }

    public void update(List<CashflowRow> rows, SupportedCurrency currency) {
        this.currency = currency;
        setItems(rows);
    }
}
