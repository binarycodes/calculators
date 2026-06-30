package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.component.popover.PopoverVariant;
import com.vaadin.flow.component.shared.Tooltip;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link Grid} with two header affordances:
 *
 * <ul>
 *   <li>A cog-icon <b>column chooser</b>: subclasses register each column under
 *       its header via {@link #track(String, Column)}; insertion order is
 *       preserved so the chooser lists columns in render order.</li>
 *   <li>An optional <b>row-colour legend</b>: subclasses that tint rows by
 *       part-name register each colour's meaning via
 *       {@link #trackRowLegend(String, String)}. When at least one entry is
 *       registered, an info-icon button appears beside the cog and opens a
 *       popover mapping each row background colour to its meaning — a key for
 *       the grid, mirroring a chart legend.</li>
 * </ul>
 *
 * <p>The parent view places {@link #createControls()} beside the grid header.</p>
 */
public abstract class BaseGrid<T> extends Grid<T> {

    /** One row-background legend entry: a swatch tinted like {@code partName} rows
     *  plus a translated description of what that colour means. */
    private record RowLegendEntry(String partName, String descriptionKey) {
    }

    private final Map<String, Column<T>> columnsByHeader = new LinkedHashMap<>();
    private final List<RowLegendEntry> rowLegend = new ArrayList<>();

    protected BaseGrid(Class<T> beanType, boolean autoCreateColumns) {
        super(beanType, autoCreateColumns);
    }

    /** Register a column so the chooser can toggle it; returns the column for chaining. */
    protected Column<T> track(String header, Column<T> column) {
        this.columnsByHeader.put(header, column);
        return column;
    }

    /**
     * Register a row-colour legend entry. {@code partName} is the part-name the
     * row {@code setPartNameGenerator} assigns (so the legend swatch can be tinted
     * to match), and {@code descriptionKey} is the translation key describing what
     * the colour means. Insertion order is preserved; only grids with at least one
     * entry show the legend button.
     */
    protected void trackRowLegend(String partName, String descriptionKey) {
        this.rowLegend.add(new RowLegendEntry(partName, descriptionKey));
    }

    /**
     * The grid's header controls: the row-colour legend (only when entries are
     * registered) followed by the column chooser. When there is no legend, this is
     * just the column-chooser menu, so legend-less grids look exactly as before.
     */
    public Component createControls() {
        final MenuBar columnChooser = createColumnChooser();
        if (this.rowLegend.isEmpty()) {
            return columnChooser;
        }
        final Button legendTrigger = createLegendTrigger();
        final Popover legendPopover = createLegendPopover(legendTrigger);
        final HorizontalLayout controls = new HorizontalLayout(legendTrigger, legendPopover, columnChooser);
        controls.setSpacing(false);
        controls.setPadding(false);
        controls.setAlignItems(FlexComponent.Alignment.CENTER);
        controls.getStyle().set("gap", "0.25rem");
        return controls;
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

    private Button createLegendTrigger() {
        final Button trigger = new Button(VaadinIcon.INFO_CIRCLE_O.create());
        trigger.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        trigger.setAriaLabel(getTranslation("grid.rowLegend"));
        Tooltip.forComponent(trigger).setText(getTranslation("grid.rowLegend"));
        return trigger;
    }

    private Popover createLegendPopover(Component target) {
        final Popover popover = new Popover();
        popover.setTarget(target);
        popover.setPosition(PopoverPosition.BOTTOM_END);
        popover.addThemeVariants(PopoverVariant.ARROW);
        popover.setAriaLabel(getTranslation("grid.rowLegend"));

        final VerticalLayout items = new VerticalLayout();
        items.addClassName("row-legend");
        items.setSpacing(false);
        items.setPadding(false);
        for (final RowLegendEntry entry : this.rowLegend) {
            final Div swatch = new Div();
            swatch.addClassNames("legend-swatch", entry.partName());
            final Span label = new Span(getTranslation(entry.descriptionKey()));
            final HorizontalLayout item = new HorizontalLayout(swatch, label);
            item.addClassName("legend-item");
            item.setAlignItems(FlexComponent.Alignment.CENTER);
            items.add(item);
        }
        popover.add(items);
        return popover;
    }
}
