package io.binarycodes.calculators.loan.ui;

import com.vaadin.flow.component.grid.ColumnTextAlign;
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

        track("Year", addColumn(LoanYear::year).setHeader("Year").setAutoWidth(true));
        track("EMI Paid", addColumn(MoneyCells.monthlyAndYearly(LoanYear::emiPaid, this.preferences::currency))
                .setHeader("EMI Paid").setTextAlign(ColumnTextAlign.END));
        track("Principal", addMoneyColumn("Principal", LoanYear::principalPaid));
        track("Interest", addMoneyColumn("Interest", LoanYear::interestPaid));
        track("Prepayment", addColumn(MoneyCells.monthlyAndYearly(LoanYear::prepayment, this.preferences::currency))
                .setHeader("Prepayment").setTextAlign(ColumnTextAlign.END));
        track("Balance", addMoneyColumn("Balance", LoanYear::endBalance));

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
            return "—";
        }
        return MoneyFormatter.format(amount, currency);
    }
}
