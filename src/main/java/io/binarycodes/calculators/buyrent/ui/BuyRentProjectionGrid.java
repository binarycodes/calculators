package io.binarycodes.calculators.buyrent.ui;

import com.vaadin.flow.component.grid.ColumnTextAlign;
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.BaseGrid;
import io.binarycodes.calculators.buyrent.domain.BuyRentYear;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

/**
 * Year-by-year projection grid. Highlights two rows, explained by the
 * row-colour legend: the cash-flow crossover (the first year owning is cheaper
 * to hold than renting) and the net-worth break-even (the first year buy equity
 * overtakes the rent portfolio).
 */
public class BuyRentProjectionGrid extends BaseGrid<BuyRentYear> {

    private final UserPreferences preferences;
    private int breakEvenYear = -1;
    private int cashFlowCrossoverYear = -1;

    public BuyRentProjectionGrid(UserPreferences preferences) {
        super(BuyRentYear.class, false);
        this.preferences = preferences;

        track(Translations.get("grid.col.year"), addColumn(BuyRentYear::year)
                .setHeader(Translations.get("grid.col.year"))
                .setAutoWidth(true));
        addMoneyColumn(Translations.get("grid.col.homeValue"), BuyRentYear::homeValue);
        addMoneyColumn(Translations.get("grid.col.mortgageBalance"), BuyRentYear::mortgageBalance);
        addMoneyColumn(Translations.get("grid.col.buyNetWorth"), BuyRentYear::equityAfterTax);
        addMoneyColumn(Translations.get("grid.col.rentPortfolio"), BuyRentYear::rentPortfolioAfterTax);
        addMoneyColumn(Translations.get("grid.col.difference"), BuyRentYear::netDifference);

        getColumns().forEach(column -> {
            column.setAutoWidth(true);
            column.setFlexGrow(1);
        });
        setAllRowsVisible(true);
        setWidthFull();

        setPartNameGenerator(this::rowParts);
        trackRowLegend("cash-flow-crossover", "legend.cashFlowCrossover");
        trackRowLegend("break-even", "legend.breakEven");
    }

    public void update(List<BuyRentYear> rows, int breakEvenYear, int cashFlowCrossoverYear) {
        this.breakEvenYear = breakEvenYear;
        this.cashFlowCrossoverYear = cashFlowCrossoverYear;
        setItems(rows);
    }

    private String rowParts(BuyRentYear row) {
        final boolean crossover = row.year() == this.cashFlowCrossoverYear;
        final boolean breakEven = row.year() == this.breakEvenYear;
        if (crossover && breakEven) {
            return "cash-flow-crossover break-even";
        }
        if (crossover) {
            return "cash-flow-crossover";
        }
        return breakEven ? "break-even" : null;
    }

    private void addMoneyColumn(String header, Function<BuyRentYear, BigDecimal> accessor) {
        track(header, addColumn(row -> formatMoney(accessor.apply(row)))
                .setHeader(header)
                .setTextAlign(ColumnTextAlign.END));
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) {
            return getTranslation("common.dash");
        }
        final SupportedCurrency currency = this.preferences.currency();
        return MoneyFormatter.format(value, currency);
    }
}
