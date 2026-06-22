package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.html.Span;

/**
 * A form-section {@link Card} that anchors a one-line validation message at its
 * top-right (the header-suffix slot). The message reports the validity of the
 * card's own inputs — a generic "fix the highlighted fields" note, a card-level
 * rule (e.g. allocations must sum to 100%), or a calculator error attributed to
 * this card. It stays hidden until {@link #showError(String)} is given a
 * non-blank message.
 */
public class FormCard extends Card {

    private final Span message = new Span();

    public FormCard(String title) {
        setTitle(title);
        this.message.addClassName("form-card-message");
        this.message.setVisible(false);
        setHeaderSuffix(this.message);
    }

    /** Show {@code error} at the top-right, or hide the slot when it is null/blank. */
    public void showError(String error) {
        final boolean hasError = error != null && !error.isBlank();
        this.message.setText(hasError ? error : "");
        this.message.setVisible(hasError);
    }

    /**
     * True when a field directly owned by this card is invalid. "Directly" stops
     * at nested {@link FormCard}s so an inner card's error isn't also blamed on
     * its outer card.
     */
    public boolean hasInvalidField() {
        return hasInvalidField(this);
    }

    private static boolean hasInvalidField(Component root) {
        return root.getChildren().anyMatch(child -> {
            if (child instanceof FormCard) {
                return false; // a nested card reports its own errors
            }
            if (child instanceof HasValidation validation && validation.isInvalid()) {
                return true;
            }
            return hasInvalidField(child);
        });
    }

    /**
     * Show the generic "fix the highlighted fields" note on every {@link FormCard}
     * under {@code root} whose own fields are invalid, and clear the rest. Callers
     * layer card-specific rules and calculator errors on top afterwards.
     */
    public static void refreshGenericErrors(Component root) {
        forEachCard(root, card ->
                card.showError(card.hasInvalidField() ? "Fix the highlighted fields" : null));
    }

    private static void forEachCard(Component root, java.util.function.Consumer<FormCard> action) {
        root.getChildren().forEach(child -> {
            if (child instanceof FormCard card) {
                action.accept(card);
            }
            forEachCard(child, action);
        });
    }

    /** The first {@link FormCard} under {@code root} in document order, if any. */
    public static java.util.Optional<FormCard> firstCard(Component root) {
        return root.getChildren()
                .map(child -> child instanceof FormCard card
                        ? java.util.Optional.of(card)
                        : firstCard(child))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .findFirst();
    }
}
