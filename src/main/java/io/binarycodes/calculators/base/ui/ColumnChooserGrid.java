package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.shared.Tooltip;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A {@link Grid} that can build a cog-icon column chooser. Subclasses register
 * each column under its header via {@link #track(String, Column)} as they add
 * it; insertion order is preserved so the chooser lists columns in the same
 * order the grid renders them. The parent view places the menu returned by
 * {@link #createColumnChooser()} beside the grid header.
 */
public abstract class ColumnChooserGrid<T> extends Grid<T> {

    private final Map<String, Column<T>> columnsByHeader = new LinkedHashMap<>();

    protected ColumnChooserGrid(Class<T> beanType, boolean autoCreateColumns) {
        super(beanType, autoCreateColumns);
    }

    /** Register a column so the chooser can toggle it; returns the column for chaining. */
    protected Column<T> track(String header, Column<T> column) {
        this.columnsByHeader.put(header, column);
        return column;
    }

    /**
     * A button-style menu listing every tracked column with a checkable toggle.
     * Toggling an item hides or shows the corresponding grid column.
     */
    public MenuBar createColumnChooser() {
        final MenuBar menuBar = new MenuBar();
        menuBar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY, MenuBarVariant.LUMO_ICON);
        final var rootItem = menuBar.addItem(VaadinIcon.COG.create());
        Tooltip.forComponent(rootItem).setText(getTranslation("grid.chooseColumns"));
        rootItem.getElement().setAttribute("aria-label", getTranslation("grid.chooseColumns"));

        final SubMenu submenu = rootItem.getSubMenu();
        for (final var entry : this.columnsByHeader.entrySet()) {
            final Column<T> column = entry.getValue();
            final var item = submenu.addItem(entry.getKey());
            item.setCheckable(true);
            item.setChecked(column.isVisible());
            item.addClickListener(event -> column.setVisible(item.isChecked()));
        }
        return menuBar;
    }
}
