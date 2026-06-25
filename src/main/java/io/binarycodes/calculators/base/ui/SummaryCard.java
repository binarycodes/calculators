package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.card.CardVariant;
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

    /**
     * As {@link #SummaryCard(String)}, with a leading icon to the left of the
     * label/value column. The icon goes in the media slot and the card switches
     * to the horizontal variant, so the value lines up under the label rather
     * than under the icon.
     */
    public SummaryCard(String labelText, Icon icon) {
        this(labelText);
        addThemeVariants(CardVariant.HORIZONTAL);
        icon.addClassName("summary-card-icon");
        setMedia(icon);
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
