package io.binarycodes.calculators.loan.ui;

import com.vaadin.flow.component.grid.ColumnTextAlign;
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.NumberToWords;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.ColumnChooserGrid;
import io.binarycodes.calculators.base.ui.MoneyCells;
import io.binarycodes.calculators.loan.domain.LoanYear;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

/**
 * Year-by-year amortization grid for the reduce-tenure schedule: EMI, interest,
 * principal, any prepayment, and the closing balance. Money cells are
 * right-aligned with a words tooltip. {@link #createColumnChooser()} returns the
 * cog-menu the view places beside the grid header.
 */
public class LoanProjectionGrid extends ColumnChooserGrid<LoanYear> {

    private final UserPreferences preferences;

    public LoanProjectionGrid(UserPreferences preferences) {
        super(LoanYear.class, false);
        this.preferences = preferences;

        track(Translations.get("grid.col.year"), addColumn(LoanYear::year).setHeader(Translations.get("grid.col.year")).setAutoWidth(true));
        track(Translations.get("grid.col.emiPaid"), addColumn(MoneyCells.monthlyAndYearly(LoanYear::emiPaid, LoanYear::monthsInPeriod, this.preferences::currency))
                .setHeader(Translations.get("grid.col.emiPaid")).setTextAlign(ColumnTextAlign.END));
        track(Translations.get("grid.col.principal"), addMoneyColumn(Translations.get("grid.col.principal"), LoanYear::principalPaid));
        track(Translations.get("grid.col.interest"), addMoneyColumn(Translations.get("grid.col.interest"), LoanYear::interestPaid));
        track(Translations.get("grid.col.prepayment"), addColumn(MoneyCells.monthlyAndYearly(LoanYear::prepayment, LoanYear::monthsInPeriod, this.preferences::currency))
                .setHeader(Translations.get("grid.col.prepayment")).setTextAlign(ColumnTextAlign.END));
        track(Translations.get("grid.col.balance"), addMoneyColumn(Translations.get("grid.col.balance"), LoanYear::endBalance));

        getColumns().forEach(column -> {
            column.setAutoWidth(true);
            column.setFlexGrow(1);
        });
        setAllRowsVisible(true);
        setWidthFull();
    }

    public void update(List<LoanYear> rows) {
        setItems(rows);
    }

    private Column<LoanYear> addMoneyColumn(String header, Function<LoanYear, BigDecimal> accessor) {
        return addColumn(row -> moneyOrDash(accessor.apply(row), this.preferences.currency()))
                .setHeader(header)
                .setTextAlign(ColumnTextAlign.END)
                .setTooltipGenerator(row -> wordsTooltip(accessor.apply(row)));
    }

    private String wordsTooltip(BigDecimal amount) {
        if (amount == null || amount.signum() == 0) {
            return "";
        }
        return NumberToWords.amountInWords(amount, this.preferences.currency());
    }

    private static String moneyOrDash(BigDecimal amount, SupportedCurrency currency) {
        if (amount == null || amount.signum() == 0) {
            return Translations.get("common.dash");
        }
        return MoneyFormatter.format(amount, currency);
    }
}
