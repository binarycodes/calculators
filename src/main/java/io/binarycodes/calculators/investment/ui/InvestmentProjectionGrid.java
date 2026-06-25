package io.binarycodes.calculators.investment.ui;

import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.NumberToWords;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.ColumnChooserGrid;
import io.binarycodes.calculators.base.ui.MoneyCells;
import io.binarycodes.calculators.investment.domain.InvestmentYear;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

/**
 * Year-by-year investment projection grid. The Phase column shows whether the
 * year is still accumulating or just holding; money cells are right-aligned and
 * carry a words tooltip. {@link #createColumnChooser()} returns a cog-menu the
 * parent view places beside the grid header to toggle columns.
 */
public class InvestmentProjectionGrid extends ColumnChooserGrid<InvestmentYear> {

    private final UserPreferences preferences;

    public InvestmentProjectionGrid(UserPreferences preferences) {
        super(InvestmentYear.class, false);
        this.preferences = preferences;

        track("Year", addColumn(InvestmentYear::year).setHeader("Year").setAutoWidth(true));
        track("Phase", addComponentColumn(InvestmentProjectionGrid::phaseBadge).setHeader("Phase"));
        track("Contribution", addColumn(MoneyCells.monthlyAndYearly(
                        InvestmentYear::contribution, this.preferences::currency))
                .setHeader("Contribution")
                .setTextAlign(ColumnTextAlign.END));
        track("Balance", addMoneyColumn("Balance", InvestmentYear::balance));
        track("Principal", addMoneyColumn("Principal", InvestmentYear::principal));
        track("Gains", addMoneyColumn("Gains", InvestmentYear::gains));
        track("Real Value", addMoneyColumn("Real Value", InvestmentYear::realValue));

        getColumns().forEach(column -> {
            column.setAutoWidth(true);
            column.setFlexGrow(1);
        });
        setAllRowsVisible(true);
        setWidthFull();
    }

    public void update(List<InvestmentYear> rows) {
        setItems(rows);
    }

    private Column<InvestmentYear> addMoneyColumn(String header,
                                                 Function<InvestmentYear, BigDecimal> accessor) {
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

    private static Badge phaseBadge(InvestmentYear row) {
        if (row.phase() == InvestmentYear.Phase.INVESTING) {
            final Badge badge = new Badge("Investing");
            badge.addThemeVariants(BadgeVariant.SUCCESS, BadgeVariant.SMALL);
            return badge;
        }
        final Badge badge = new Badge("Holding");
        badge.addThemeVariants(BadgeVariant.SMALL);
        return badge;
    }
}
