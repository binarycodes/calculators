package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.popover.Popover;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The retirement projection grid tints rows by lifecycle phase, so its header
 * controls must surface a three-entry row-colour legend (retirement / low /
 * depleted).
 */
class ProjectionGridLegendTest {

    @Test
    void controls_expose_a_three_entry_row_legend() {
        final Component controls = new ProjectionGrid(new UserPreferences()).createControls();
        final Popover popover = controls.getChildren()
                .filter(Popover.class::isInstance)
                .map(Popover.class::cast)
                .findFirst()
                .orElse(null);
        assertNotNull(popover, "retirement grid must show a row-colour legend");
        final long legendRows = popover.getChildren().findFirst().orElseThrow().getChildren().count();
        assertEquals(3, legendRows, "retirement / low-corpus / depleted");
    }
}
