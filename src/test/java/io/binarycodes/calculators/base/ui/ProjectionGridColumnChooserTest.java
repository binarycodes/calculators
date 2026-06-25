package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.menubar.MenuBar;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import io.binarycodes.calculators.buyrent.ui.BuyRentProjectionGrid;
import io.binarycodes.calculators.goal.ui.GoalProjectionGrid;
import io.binarycodes.calculators.investment.ui.InvestmentProjectionGrid;
import io.binarycodes.calculators.loan.ui.LoanProjectionGrid;
import io.binarycodes.calculators.retirement.ui.ProjectionGrid;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every year-by-year projection grid must offer a column chooser that lists
 * all of its columns. Guards against a new (or existing) grid shipping without
 * the cog-menu — the inconsistency that left Buy vs Rent without one.
 */
class ProjectionGridColumnChooserTest {

    private static Stream<GridUnderTest> grids() {
        final UserPreferences preferences = new UserPreferences();
        final var retirement = new ProjectionGrid(preferences);
        final var investment = new InvestmentProjectionGrid(preferences);
        final var loan = new LoanProjectionGrid(preferences);
        final var goal = new GoalProjectionGrid(preferences);
        final var buyrent = new BuyRentProjectionGrid(preferences);
        return Stream.of(
                new GridUnderTest("retirement", retirement, retirement.createColumnChooser()),
                new GridUnderTest("investment", investment, investment.createColumnChooser()),
                new GridUnderTest("loan", loan, loan.createColumnChooser()),
                new GridUnderTest("goal", goal, goal.createColumnChooser()),
                new GridUnderTest("buyrent", buyrent, buyrent.createColumnChooser()));
    }

    @Test
    void every_projection_grid_exposes_a_chooser_for_all_its_columns() {
        grids().forEach(ProjectionGridColumnChooserTest::assertChoosesAllColumns);
    }

    private static void assertChoosesAllColumns(GridUnderTest target) {
        final List<MenuItem> rootItems = target.chooser().getItems();
        assertEquals(1, rootItems.size(), target.name() + ": chooser should be a single cog menu");

        final List<MenuItem> columnItems = rootItems.getFirst().getSubMenu().getItems();
        assertEquals(target.grid().getColumns().size(), columnItems.size(),
                target.name() + ": chooser must list every grid column");
        assertTrue(columnItems.stream().allMatch(item -> item.isCheckable() && item.isChecked()),
                target.name() + ": every column toggle must start checkable and checked");
    }

    private record GridUnderTest(String name, Grid<?> grid, MenuBar chooser) {
    }
}
