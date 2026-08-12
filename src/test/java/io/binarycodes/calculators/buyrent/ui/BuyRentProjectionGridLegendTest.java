package io.binarycodes.calculators.buyrent.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.popover.Popover;
import io.binarycodes.calculators.base.prefs.UserPreferences;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The buy-vs-rent grid tints two rows — the cash-flow crossover and the
 * net-worth break-even — so its header controls must surface a two-entry
 * row-colour legend explaining both tints.
 */
class BuyRentProjectionGridLegendTest {

    @Test
    void controls_expose_both_row_legends() {
        final Component controls = new BuyRentProjectionGrid(new UserPreferences()).createControls();
        final Popover popover = controls.getChildren()
                .filter(Popover.class::isInstance)
                .map(Popover.class::cast)
                .findFirst()
                .orElse(null);
        assertNotNull(popover, "buy-vs-rent grid must show a row-colour legend");
        final long legendRows = popover.getChildren().findFirst().orElseThrow().getChildren().count();
        assertEquals(2, legendRows, "the cash-flow crossover and break-even row tints");
    }
}
