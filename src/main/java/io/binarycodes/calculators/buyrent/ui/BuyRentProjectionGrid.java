package io.binarycodes.calculators.buyrent.ui;

import com.vaadin.flow.component.grid.ColumnTextAlign;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.ColumnChooserGrid;
import io.binarycodes.calculators.buyrent.domain.BuyRentYear;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

/**
 * Year-by-year projection grid. Highlights the break-even row (the first year
 * where buy equity overtakes the rent portfolio) with a success theme variant.
 */
public class BuyRentProjectionGrid extends ColumnChooserGrid<BuyRentYear> {

    private final UserPreferences preferences;
    private int breakEvenYear = -1;

    public BuyRentProjectionGrid(UserPreferences preferences) {
        super(BuyRentYear.class, false);
        this.preferences = preferences;

        track("Year", addColumn(BuyRentYear::year)
                .setHeader("Year")
                .setAutoWidth(true));
        addMoneyColumn("Home Value", BuyRentYear::homeValue);
        addMoneyColumn("Mortgage Balance", BuyRentYear::mortgageBalance);
        addMoneyColumn("Buy Net Worth (after tax)", BuyRentYear::equityAfterTax);
        addMoneyColumn("Rent Portfolio (after tax)", BuyRentYear::rentPortfolioAfterTax);
        addMoneyColumn("Difference (Buy − Rent)", BuyRentYear::netDifference);

        getColumns().forEach(column -> {
            column.setAutoWidth(true);
            column.setFlexGrow(1);
        });
        setAllRowsVisible(true);
        setWidthFull();

        // Highlight the break-even row — the first year buy is ahead.
        setPartNameGenerator(row -> row.year() == this.breakEvenYear ? "break-even" : null);
    }

    public void update(List<BuyRentYear> rows, int breakEvenYear) {
        this.breakEvenYear = breakEvenYear;
        setItems(rows);
    }

    private void addMoneyColumn(String header, Function<BuyRentYear, BigDecimal> accessor) {
        track(header, addColumn(row -> formatMoney(accessor.apply(row)))
                .setHeader(header)
                .setTextAlign(ColumnTextAlign.END));
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) {
            return "—";
        }
        final SupportedCurrency currency = this.preferences.currency();
        return MoneyFormatter.format(value, currency);
    }
}
