package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import io.binarycodes.calculators.base.money.MoneyFormatter;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.retirement.domain.ProjectionRow;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

/**
 * Year-by-year projection grid. Money cells are formatted via the active
 * currency from {@link UserPreferences} and right-aligned; the Phase column
 * renders a {@link Badge}; rows and the Corpus (End) cell carry part-names so
 * {@code grid.css} can colour them by lifecycle phase (retirement / low /
 * depleted / healthy).
 */
public class ProjectionGrid extends Grid<ProjectionRow> {

    private static final BigDecimal LOW_CORPUS_MULTIPLIER = BigDecimal.TEN;

    private final UserPreferences preferences;

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
        addColumn(ProjectionRow::year).setHeader("Year");
        addColumn(ProjectionRow::age).setHeader("Age");
        addComponentColumn(row -> phaseBadge(row.isPost())).setHeader("Phase");
        addMoneyColumn("Annual Expenses", ProjectionRow::annualExp);
        addMoneyColumn("Corpus (Start)",  ProjectionRow::startCorpus);
        addMoneyColumn("Returns",         ProjectionRow::returns);
        addMoneyColumn("Investment",      ProjectionRow::investment);
        addMoneyColumn("Withdrawal",      ProjectionRow::withdrawal);
        addMoneyColumn("Corpus (End)",    ProjectionRow::endCorpus)
                .setPartNameGenerator(ProjectionGrid::corpusEndPartName);
    }

    private Column<ProjectionRow> addMoneyColumn(
            String header, Function<ProjectionRow, BigDecimal> moneyAccessor) {
        return addColumn(row -> moneyOrDash(moneyAccessor.apply(row), this.preferences.currency()))
                .setHeader(header)
                .setTextAlign(ColumnTextAlign.END);
    }

    private static String corpusEndPartName(ProjectionRow row) {
        if (!row.isPost()) {
            return null;
        }
        if (row.endCorpus().signum() <= 0) {
            return "corpus-end-depleted";
        }
        if (isCorpusLow(row)) {
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
        if (row.isPost() && isCorpusLow(row)) {
            return "low-row";
        }
        return null;
    }

    private static boolean isCorpusLow(ProjectionRow row) {
        return row.endCorpus().compareTo(row.annualExp().multiply(LOW_CORPUS_MULTIPLIER)) < 0;
    }

    private static String moneyOrDash(BigDecimal amount, SupportedCurrency currency) {
        if (amount == null || amount.signum() == 0) {
            return "—";
        }
        return MoneyFormatter.format(amount, currency);
    }

    private static Badge phaseBadge(boolean postRetirement) {
        final Badge badge;
        if (postRetirement) {
            badge = new Badge("Post");
        } else {
            badge = new Badge("Pre");
            badge.addThemeVariants(BadgeVariant.SUCCESS);
        }
        badge.addThemeVariants(BadgeVariant.SMALL);
        return badge;
    }
}
