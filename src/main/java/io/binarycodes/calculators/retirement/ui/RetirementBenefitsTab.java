package io.binarycodes.calculators.retirement.ui;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Placeholder for post-retirement income streams (pensions, social
 * security, annuities). Inputs and calculator integration TBD.
 */
class RetirementBenefitsTab extends VerticalLayout {

    RetirementBenefitsTab() {
        setPadding(true);
        final var placeholder = new Span(
                "Pensions, social security, annuities, and other post-retirement income — coming soon.");
        placeholder.getStyle().setColor("var(--vaadin-secondary-text-color, #71717a)");
        add(placeholder);
    }
}
