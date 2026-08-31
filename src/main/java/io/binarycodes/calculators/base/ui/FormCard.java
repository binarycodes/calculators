package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import io.binarycodes.calculators.base.i18n.Translations;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * A form-section {@link Card} whose header carries, at its top-right, a
 * one-line validation message and an icon button that clears just this
 * section. The message reports the validity of the card's own inputs — a
 * generic "fix the highlighted fields" note, a card-level rule (e.g.
 * allocations must sum to 100%), or a calculator error attributed to this
 * card. It stays hidden until {@link #showError(String)} is given a non-blank
 * message.
 */
public class FormCard extends Card {

    private final Span message = new Span();
    private Runnable clearAction = this::blankOwnFields;

    public FormCard(String title) {
        setTitle(title);
        this.message.addClassName("form-card-message");
        this.message.setVisible(false);
        setHeaderSuffix(buildHeaderSuffix());
    }

    /** Show {@code error} at the top-right, or hide the slot when it is null/blank. */
    public void showError(String error) {
        final boolean hasError = error != null && !error.isBlank();
        this.message.setText(hasError ? error : "");
        this.message.setVisible(hasError);
    }

    /**
     * Replace what the header's clear button does. The default blanks the
     * card's own fields, which is right for a section of plain inputs;
     * sections holding a row list, or a field whose empty value is not a
     * sensible starting point, pass their own reset here.
     */
    public void onClear(Runnable action) {
        this.clearAction = action;
    }

    /** Blank every field this card owns directly; nested {@link FormCard}s keep their values. */
    public void blankOwnFields() {
        blankFields(this);
    }

    /**
     * True when a field directly owned by this card is invalid. "Directly" stops
     * at nested {@link FormCard}s so an inner card's error isn't also blamed on
     * its outer card.
     */
    public boolean hasInvalidField() {
        return hasInvalidField(this);
    }

    private Component buildHeaderSuffix() {
        final String clearLabel = Translations.get("action.clearSection");

        final Button clearButton = new Button(VaadinIcon.ERASER.create(), event -> this.clearAction.run());
        clearButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        clearButton.addClassName("form-card-clear");
        clearButton.setTooltipText(clearLabel);
        clearButton.setAriaLabel(clearLabel);

        final HorizontalLayout suffix = new HorizontalLayout(this.message, clearButton);
        suffix.addClassName("form-card-header-suffix");
        suffix.setSpacing(true);
        suffix.setAlignItems(FlexComponent.Alignment.CENTER);
        return suffix;
    }

    private static void blankFields(Component root) {
        root.getChildren().forEach(child -> {
            if (child instanceof FormCard) {
                return; // a nested card clears itself from its own header
            }
            if (child instanceof HasValue<?, ?> field) {
                field.clear();
            } else {
                blankFields(child);
            }
        });
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
                card.showError(card.hasInvalidField()
                        ? Translations.get("form.fixHighlighted")
                        : null));
    }

    private static void forEachCard(Component root, Consumer<FormCard> action) {
        root.getChildren().forEach(child -> {
            if (child instanceof FormCard card) {
                action.accept(card);
            }
            forEachCard(child, action);
        });
    }

    /** The first {@link FormCard} under {@code root} in document order, if any. */
    public static Optional<FormCard> firstCard(Component root) {
        return root.getChildren()
                .map(child -> child instanceof FormCard card
                        ? Optional.of(card)
                        : firstCard(child))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }
}
