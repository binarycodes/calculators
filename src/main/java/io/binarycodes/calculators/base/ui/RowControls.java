package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * Shared controls for repeating input rows (the {@code form-row} pattern).
 */
public final class RowControls {

    private RowControls() {
    }

    /**
     * A delete button for one row. It carries a trash icon plus a "Remove"
     * label; {@code form-row} CSS hides the label while the row sits on a
     * single line (compact, icon-only look) and reveals it once the row
     * collapses into a stacked card, where a bare icon would read oddly.
     */
    public static Button removeButton(Runnable onRemove) {
        final String removeLabel = io.binarycodes.calculators.base.i18n.Translations.get("row.remove");
        final Span label = new Span(removeLabel);
        label.addClassName("remove-label");

        final Button button = new Button(VaadinIcon.TRASH.create(), event -> onRemove.run());
        button.getElement().appendChild(label.getElement());
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        button.addClassName("row-remove");
        button.getElement().setAttribute("aria-label", removeLabel);
        return button;
    }
}
