package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.popover.Popover;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The grid header shows a row-colour legend popover only when the grid actually
 * tints rows (registers legend entries); a legend-less grid keeps just the
 * column-chooser cog.
 */
class BaseGridLegendTest {

    private static final class SampleGrid extends BaseGrid<String> {
        SampleGrid(boolean withLegend) {
            super(String.class, false);
            track("Name", addColumn(value -> value).setHeader("Name"));
            if (withLegend) {
                trackRowLegend("low-row", "legend.lowCorpus");
                trackRowLegend("depleted-row", "legend.depleted");
            }
        }
    }

    private static Popover legendPopover(Component controls) {
        return controls.getChildren()
                .filter(Popover.class::isInstance)
                .map(Popover.class::cast)
                .findFirst()
                .orElse(null);
    }

    private static long legendRowCount(Popover popover) {
        // The popover holds one content layout whose children are the legend rows.
        return popover.getChildren().findFirst().orElseThrow().getChildren().count();
    }

    @Test
    void controls_expose_a_legend_with_one_row_per_registered_colour() {
        final Component controls = new SampleGrid(true).createControls();
        assertInstanceOf(HorizontalLayout.class, controls);
        final Popover popover = legendPopover(controls);
        assertNotNull(popover, "legend popover must be present when entries are registered");
        assertEquals(2, legendRowCount(popover), "one legend row per registered colour");
    }

    @Test
    void controls_are_just_the_column_chooser_when_no_colours_are_registered() {
        final Component controls = new SampleGrid(false).createControls();
        assertInstanceOf(MenuBar.class, controls);
        assertTrue(controls.getChildren().noneMatch(Popover.class::isInstance),
                "no legend popover when no colours are registered");
    }
}
