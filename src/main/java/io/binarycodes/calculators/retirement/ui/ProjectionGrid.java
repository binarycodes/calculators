package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.shared.Tooltip;
import com.vaadin.flow.data.renderer.LitRenderer;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.NumberToWords;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.retirement.domain.ProjectionRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Year-by-year projection grid. Money cells are formatted via the active
 * currency from {@link UserPreferences} and right-aligned; the Phase column
 * renders a {@link Badge}; rows and the Corpus (End) cell carry part-names so
 * {@code grid.css} can colour them by lifecycle phase (retirement / low /
 * depleted / healthy).
 */
public class ProjectionGrid extends Grid<ProjectionRow> {

    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);

    private final UserPreferences preferences;
    // Header → column; insertion order preserved so the chooser lists columns
    // in the same order as the grid renders them.
    private final Map<String, Column<ProjectionRow>> columnsByHeader = new LinkedHashMap<>();

    public ProjectionGrid(UserPreferences preferences) {
        super(ProjectionRow.class, false);
        this.preferences = preferences;
        configureColumns();
        setPartNameGenerator(ProjectionGrid::projectionRowPartName);
        getColumns().forEach(column -> {
            column.setAutoWidth(true);
            column.setFlexGrow(1);
        });
        setAllRowsVisible(true);
        setWidthFull();
    }

    public void update(List<ProjectionRow> rows) {
        setItems(rows);
    }

    private void configureColumns() {
        track("Year",          addColumn(ProjectionRow::year).setHeader("Year"));
        track("Age",           addColumn(ProjectionRow::age).setHeader("Age"));
        track("Phase",         addComponentColumn(ProjectionGrid::phaseBadge).setHeader("Phase"));
        track("Expenses",      addMonthlyAndYearlyColumn("Expenses", ProjectionRow::annualExp));
        track("Corpus (Start)", addMoneyColumn("Corpus (Start)", ProjectionRow::startCorpus));
        track("Returns",       addMonthlyAndYearlyColumn("Returns", ProjectionRow::returns));
        track("Investment",    addMonthlyAndYearlyColumn("Investment", ProjectionRow::investment));
        track("Withdrawal",    addMonthlyAndYearlyColumn("Withdrawal", ProjectionRow::withdrawal));
        track("Tax Paid",      addMoneyColumn("Tax Paid", ProjectionRow::taxPaid));
        track("Corpus (End)",  addMoneyColumn("Corpus (End)", ProjectionRow::endCorpus)
                .setPartNameGenerator(ProjectionGrid::corpusEndPartName));
    }

    private void track(String header, Column<ProjectionRow> column) {
        this.columnsByHeader.put(header, column);
    }

    /**
     * A button-style menu listing every column with a checkable toggle.
     * Toggling an item hides or shows the corresponding grid column. Intended
     * to be placed adjacent to (or above) the grid by the parent view.
     */
    public MenuBar createColumnChooser() {
        final MenuBar menuBar = new MenuBar();
        menuBar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY, MenuBarVariant.LUMO_ICON);
        final var rootItem = menuBar.addItem(VaadinIcon.COG.create());
        Tooltip.forComponent(rootItem).setText("Choose columns");
        rootItem.getElement().setAttribute("aria-label", "Choose columns");

        final SubMenu submenu = rootItem.getSubMenu();
        for (final var entry : this.columnsByHeader.entrySet()) {
            final Column<ProjectionRow> column = entry.getValue();
            final var item = submenu.addItem(entry.getKey());
            item.setCheckable(true);
            item.setChecked(column.isVisible());
            item.addClickListener(e -> column.setVisible(item.isChecked()));
        }
        return menuBar;
    }

    private Column<ProjectionRow> addMoneyColumn(String header, Function<ProjectionRow, BigDecimal> moneyAccessor) {
        return addColumn(row -> moneyOrDash(moneyAccessor.apply(row), this.preferences.currency()))
                .setHeader(header)
                .setTextAlign(ColumnTextAlign.END)
                .setTooltipGenerator(row -> wordsTooltip(moneyAccessor.apply(row)));
    }

    private Column<ProjectionRow> addMonthlyAndYearlyColumn(String header, Function<ProjectionRow, BigDecimal> yearlyAccessor) {
        // Each line carries its own ``title`` attribute so hovering over the
        // monthly or yearly value surfaces the amount in words for that line.
        final LitRenderer<ProjectionRow> renderer = LitRenderer.<ProjectionRow>of(
                        """
                                <div class="money-cell">
                                    <div id="monthly_${item.id}" class="money-cell-primary">${item.monthly}</div>
                                    <div id="yearly_${item.id}" class="money-cell-secondary">${item.yearly}</div>
                                </div>
                                
                                <vaadin-tooltip for="monthly_${item.id}" text="${item.monthlyWords}"></vaadin-tooltip>
                                <vaadin-tooltip for="yearly_${item.id}" text="${item.yearlyWords}"></vaadin-tooltip>
                                """)
                .withProperty("id", row -> UUID.randomUUID().toString())
                .withProperty("monthly", row -> formatMonthly(yearlyAccessor.apply(row)))
                .withProperty("yearly", row -> formatYearly(yearlyAccessor.apply(row)))
                .withProperty("monthlyWords", row -> wordsTooltip(monthlyOf(yearlyAccessor.apply(row))))
                .withProperty("yearlyWords", row -> wordsTooltip(yearlyAccessor.apply(row)));

        return addColumn(renderer)
                .setHeader(header)
                .setTextAlign(ColumnTextAlign.END);
    }

    private String formatMonthly(BigDecimal yearlyAmount) {
        if (yearlyAmount == null || yearlyAmount.signum() == 0) {
            return "—";
        }
        return MoneyFormatter.format(monthlyOf(yearlyAmount), this.preferences.currency()) + " /mo";
    }

    private String formatYearly(BigDecimal yearlyAmount) {
        if (yearlyAmount == null || yearlyAmount.signum() == 0) {
            return "";
        }
        return MoneyFormatter.format(yearlyAmount, this.preferences.currency()) + " /yr";
    }

    private static BigDecimal monthlyOf(BigDecimal yearlyAmount) {
        if (yearlyAmount == null) {
            return BigDecimal.ZERO;
        }
        return yearlyAmount.divide(MONTHS_PER_YEAR, 0, RoundingMode.HALF_UP);
    }

    private String wordsTooltip(BigDecimal amount) {
        if (amount == null || amount.signum() == 0) {
            return "";
        }
        return NumberToWords.amountInWords(amount, this.preferences.currency());
    }

    private static String corpusEndPartName(ProjectionRow row) {
        if (!row.isPost()) {
            return null;
        }
        if (row.endCorpus().signum() <= 0) {
            return "corpus-end-depleted";
        }
        if (row.lowCorpus()) {
            return "corpus-end-low";
        }
        return "corpus-end-healthy";
    }

    private static String projectionRowPartName(ProjectionRow row) {
        if (row.depleted()) {
            return "depleted-row";
        }
        if (row.isRetireYear()) {
            return "retirement-row";
        }
        if (row.isPost() && row.lowCorpus()) {
            return "low-row";
        }
        return null;
    }

    private static String moneyOrDash(BigDecimal amount, SupportedCurrency currency) {
        if (amount == null || amount.signum() == 0) {
            return "—";
        }
        return MoneyFormatter.format(amount, currency);
    }

    private static Badge phaseBadge(ProjectionRow row) {
        final Badge badge;
        if (row.isPost()) {
            badge = new Badge("Post");
        } else {
            badge = new Badge("Pre");
            badge.addThemeVariants(BadgeVariant.SUCCESS);
        }

        if (row.depleted()) {
            badge.addThemeVariants(BadgeVariant.ERROR);
        } else if (row.lowCorpus()) {
            badge.addThemeVariants(BadgeVariant.WARNING);
        }

        badge.addThemeVariants(BadgeVariant.SMALL);
        return badge;
    }
}
