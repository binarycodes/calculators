package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.data.renderer.LitRenderer;
import io.binarycodes.calculators.base.i18n.Translations;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.NumberToWords;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.base.ui.BaseGrid;
import io.binarycodes.calculators.retirement.domain.ProjectionRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Year-by-year projection grid. Money cells are formatted via the active
 * currency from {@link UserPreferences} and right-aligned; the Phase column
 * renders a {@link Badge}; rows and the Corpus (End) cell carry part-names so
 * {@code grid.css} can colour them by lifecycle phase (retirement / low /
 * depleted / healthy).
 */
public class ProjectionGrid extends BaseGrid<ProjectionRow> {

    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);

    private final UserPreferences preferences;

    public ProjectionGrid(UserPreferences preferences) {
        super(ProjectionRow.class, false);
        this.preferences = preferences;
        configureColumns();
        setPartNameGenerator(ProjectionGrid::projectionRowPartName);
        trackRowLegend("retirement-row", "legend.retirementYear");
        trackRowLegend("low-row", "legend.lowCorpus");
        trackRowLegend("depleted-row", "legend.depleted");
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
        track(Translations.get("grid.col.year"),          addColumn(ProjectionRow::year).setHeader(Translations.get("grid.col.year")));
        track(Translations.get("grid.col.age"),           addColumn(ProjectionRow::age).setHeader(Translations.get("grid.col.age")));
        track(Translations.get("grid.col.phase"),         addComponentColumn(ProjectionGrid::phaseBadge).setHeader(Translations.get("grid.col.phase")));
        track(Translations.get("grid.col.expenses"),      addMonthlyAndYearlyColumn(Translations.get("grid.col.expenses"), ProjectionRow::annualExp));
        track(Translations.get("grid.col.corpusStart"), addMoneyColumn(Translations.get("grid.col.corpusStart"), ProjectionRow::startCorpus));
        track(Translations.get("grid.col.returns"),       addMonthlyAndYearlyColumn(Translations.get("grid.col.returns"), ProjectionRow::returns));
        track(Translations.get("grid.col.investment"),    addMonthlyAndYearlyColumn(Translations.get("grid.col.investment"), ProjectionRow::investment));
        track(Translations.get("grid.col.withdrawal"),    addMonthlyAndYearlyColumn(Translations.get("grid.col.withdrawal"), ProjectionRow::withdrawal));
        track(Translations.get("grid.col.taxPaid"),      addMoneyColumn(Translations.get("grid.col.taxPaid"), ProjectionRow::taxPaid));
        track(Translations.get("grid.col.corpusEnd"),  addMoneyColumn(Translations.get("grid.col.corpusEnd"), ProjectionRow::endCorpus)
                .setPartNameGenerator(ProjectionGrid::corpusEndPartName));
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
            return Translations.get("common.dash");
        }
        return MoneyFormatter.format(monthlyOf(yearlyAmount), this.preferences.currency()) + " " + Translations.get("unit.perMonth");
    }

    private String formatYearly(BigDecimal yearlyAmount) {
        if (yearlyAmount == null || yearlyAmount.signum() == 0) {
            return "";
        }
        return MoneyFormatter.format(yearlyAmount, this.preferences.currency()) + " " + Translations.get("unit.perYear");
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
            return Translations.get("common.dash");
        }
        return MoneyFormatter.format(amount, currency);
    }

    private static Badge phaseBadge(ProjectionRow row) {
        final Badge badge;
        if (row.isPost()) {
            badge = new Badge(Translations.get("retirement.phase.post"));
        } else {
            badge = new Badge(Translations.get("retirement.phase.pre"));
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
