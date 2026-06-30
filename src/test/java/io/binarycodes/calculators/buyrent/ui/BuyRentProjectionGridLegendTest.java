package io.binarycodes.calculators.buyrent.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.popover.Popover;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The buy-vs-rent grid tints the break-even row, so its header controls must
 * surface a one-entry row-colour legend explaining that tint.
 */
class BuyRentProjectionGridLegendTest {

    @Test
    void controls_expose_a_break_even_row_legend() {
        final Component controls = new BuyRentProjectionGrid(new UserPreferences()).createControls();
        final Popover popover = controls.getChildren()
                .filter(Popover.class::isInstance)
                .map(Popover.class::cast)
                .findFirst()
                .orElse(null);
        assertNotNull(popover, "buy-vs-rent grid must show a row-colour legend");
        final long legendRows = popover.getChildren().findFirst().orElseThrow().getChildren().count();
        assertEquals(1, legendRows, "the break-even row tint");
    }
}
