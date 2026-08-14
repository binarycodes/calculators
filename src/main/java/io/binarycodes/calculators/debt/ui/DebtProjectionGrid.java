package io.binarycodes.calculators.debt.ui;

import com.vaadin.flow.component.grid.ColumnTextAlign;
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.BaseGrid;
import io.binarycodes.calculators.debt.domain.DebtPlanYear;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

/**
 * Year-by-year schedule for the headline strategy: total balance, interest and
 * principal paid, the debts that received surplus that year, and cumulative
 * interest. The final row — the year the last debt clears — is highlighted and
 * explained by the row-colour legend.
 */
public class DebtProjectionGrid extends BaseGrid<DebtPlanYear> {

    private final UserPreferences preferences;
    private int payoffYear = -1;

    public DebtProjectionGrid(UserPreferences preferences) {
        super(DebtPlanYear.class, false);
        this.preferences = preferences;

        track(Translations.get("grid.col.year"), addColumn(DebtPlanYear::year)
                .setHeader(Translations.get("grid.col.year"))
                .setAutoWidth(true));
        addMoneyColumn(Translations.get("grid.col.debt.totalBalance"), DebtPlanYear::totalBalance);
        addMoneyColumn(Translations.get("grid.col.debt.interestPaid"), DebtPlanYear::interestPaid);
        addMoneyColumn(Translations.get("grid.col.debt.principalPaid"), DebtPlanYear::principalPaid);
        track(Translations.get("grid.col.debt.targets"), addColumn(row -> targets(row))
                .setHeader(Translations.get("grid.col.debt.targets"))
                .setFlexGrow(1));
        addMoneyColumn(Translations.get("grid.col.debt.cumulativeInterest"), DebtPlanYear::cumulativeInterest);

        getColumns().forEach(column -> {
            column.setAutoWidth(true);
            column.setFlexGrow(1);
        });
        setAllRowsVisible(true);
        setWidthFull();

        setPartNameGenerator(this::rowParts);
        trackRowLegend("debt-free", "legend.debt.debtFree");
    }

    public void update(List<DebtPlanYear> rows, int payoffYear) {
        this.payoffYear = payoffYear;
        setItems(rows);
    }

    private String rowParts(DebtPlanYear row) {
        return row.year() == this.payoffYear ? "debt-free" : null;
    }

    private String targets(DebtPlanYear row) {
        if (row.targets() == null || row.targets().isEmpty()) {
            return getTranslation("common.dash");
        }
        return String.join(", ", row.targets());
    }

    private void addMoneyColumn(String header, Function<DebtPlanYear, BigDecimal> accessor) {
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
