package io.binarycodes.calculators.goal.ui;

import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.shared.Tooltip;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.NumberToWords;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.goal.domain.GoalProjectionRow;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Year-by-year corpus build-up grid for the goal planner. Money cells are
 * right-aligned and formatted via the active currency from {@link UserPreferences};
 * a tooltip on each money cell spells the amount out in words. The Age column
 * is hidden when no age is tracked (only the AGES horizon mode populates it).
 *
 * <p>{@link #createColumnChooser()} returns a cog-menu the parent view can
 * place next to the grid header — toggling items hides or shows columns
 * without recomputing the projection.</p>
 */
public class GoalProjectionGrid extends Grid<GoalProjectionRow> {

    private final UserPreferences preferences;
    private final Column<GoalProjectionRow> ageColumn;
    private final Map<String, Column<GoalProjectionRow>> columnsByHeader = new LinkedHashMap<>();

    public GoalProjectionGrid(UserPreferences preferences) {
        super(GoalProjectionRow.class, false);
        this.preferences = preferences;

        track("Year", addColumn(GoalProjectionRow::year).setHeader("Year").setAutoWidth(true));
        this.ageColumn = addColumn(row -> row.age() == null ? "—" : row.age().toString())
                .setHeader("Age")
                .setAutoWidth(true);
        track("Age", this.ageColumn);
        track("Yearly Investment", addMoneyColumn("Yearly Investment", GoalProjectionRow::yearlyContribution));
        track("Balance",   addMoneyColumn("Balance",   GoalProjectionRow::balance));
        track("Principal", addMoneyColumn("Principal", GoalProjectionRow::principal));
        track("Gains",     addMoneyColumn("Gains",     GoalProjectionRow::gains));
        track("Tax",       addMoneyColumn("Tax",       GoalProjectionRow::taxIfWithdrawn));

        getColumns().forEach(column -> {
            column.setAutoWidth(true);
            column.setFlexGrow(1);
        });
        setAllRowsVisible(true);
        setWidthFull();
    }

    public void update(List<GoalProjectionRow> rows) {
        final boolean anyHasAge = rows.stream().anyMatch(row -> row.age() != null);
        this.ageColumn.setVisible(anyHasAge);
        setItems(rows);
    }

    /**
     * Returns a cog-icon menu that toggles each column's visibility. Place it
     * next to the grid header so the user can tailor what's shown without
     * recomputing the projection.
     */
    public MenuBar createColumnChooser() {
        final MenuBar menuBar = new MenuBar();
        menuBar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY, MenuBarVariant.LUMO_ICON);
        final var rootItem = menuBar.addItem(VaadinIcon.COG.create());
        Tooltip.forComponent(rootItem).setText("Choose columns");
        rootItem.getElement().setAttribute("aria-label", "Choose columns");

        final SubMenu submenu = rootItem.getSubMenu();
        for (final var entry : this.columnsByHeader.entrySet()) {
            final Column<GoalProjectionRow> column = entry.getValue();
            final var item = submenu.addItem(entry.getKey());
            item.setCheckable(true);
            item.setChecked(column.isVisible());
            item.addClickListener(event -> column.setVisible(item.isChecked()));
        }
        return menuBar;
    }

    private void track(String header, Column<GoalProjectionRow> column) {
        this.columnsByHeader.put(header, column);
    }

    private Column<GoalProjectionRow> addMoneyColumn(String header,
                                                    Function<GoalProjectionRow, BigDecimal> accessor) {
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
