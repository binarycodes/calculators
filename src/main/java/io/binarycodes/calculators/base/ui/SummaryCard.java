package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.html.Span;
import io.binarycodes.calculators.base.common.Status;

/**
 * A compact "label + big value" tile used in summary rows across calculators.
 * Setting a {@link Status} tints the card via the {@code status} attribute,
 * which {@code summary-card.css} reads to pick a colour.
 */
public class SummaryCard extends Card {
    private final Span label = new Span();
    private final Span value = new Span();

    public SummaryCard(String labelText) {
        addClassName("summary-card");
        this.label.setText(labelText);

        setTitle(labelText);
        add(this.value);
    }

    public void setLabel(String text) {
        this.label.setText(text);
    }

    public void setValue(String text, Status status) {
        this.value.setText(text);

        if (status != null) {
            this.getElement().setAttribute("status", status.toString().toLowerCase());
        } else {
            this.getElement().removeAttribute("status");
        }
    }
}
