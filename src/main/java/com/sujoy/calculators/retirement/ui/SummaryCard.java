package com.sujoy.calculators.retirement.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

/** A compact "label + big value" tile used in the summary row. */
public class SummaryCard extends Div {
    private final Span label = new Span();
    private final Span value = new Span();

    public SummaryCard(String labelText) {
        addClassName("summary-card");
        label.addClassName("summary-label");
        value.addClassName("summary-value");
        label.setText(labelText);
        add(label, value);
    }

    public void setLabel(String text) { label.setText(text); }

    /** {@code tone} is one of {@code null}, {@code "red"}, {@code "amber"}, {@code "green"}. */
    public void setValue(String text, String tone) {
        value.setText(text);
        value.removeClassNames("is-red", "is-amber", "is-green");
        if (tone != null) value.addClassName("is-" + tone);
    }
}
