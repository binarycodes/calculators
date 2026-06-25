package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import io.binarycodes.calculators.base.common.Status;

/**
 * A compact "label + big value" tile used in summary rows across calculators.
 * The label is the card title; {@link #setLabel} updates it live. Setting a
 * {@link Status} tints the card via the {@code status} attribute, which
 * {@code summary-card.css} reads to pick a colour.
 */
public class SummaryCard extends Card {
    private final Span value = new Span();

    public SummaryCard(String labelText) {
        addClassName("summary-card");
        setTitle(labelText);
        add(this.value);
    }

    /** As {@link #SummaryCard(String)}, with a leading icon next to the label. */
    public SummaryCard(String labelText, Icon icon) {
        this(labelText);
        icon.addClassName("summary-card-icon");
        setHeaderPrefix(icon);
    }

    public void setLabel(String text) {
        setTitle(text);
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
