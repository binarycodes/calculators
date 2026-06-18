package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.html.Span;

/**
 * A small dot appended to a {@code TabSheet} tab to mark that the tab holds
 * user-entered data, so it is obvious at a glance which tabs carry input
 * without opening each one. The dot starts hidden; a reactive effect on the
 * form toggles it as the underlying inputs change — primary when the tab holds
 * a value, red when one of its fields is invalid.
 */
public final class TabIndicator {

    private static final String ERROR_CLASS = "tab-indicator-error";

    private TabIndicator() {
    }

    /**
     * A tab that drives a {@link TabIndicator} dot. Implemented by a
     * {@link Component} tab; the defaults derive the state from the tab's own
     * fields — a value anywhere lights the dot, an invalid field turns it red —
     * so most tabs need only declare {@code implements TabIndicator.Source}.
     * Override either method for tabs that need bespoke logic.
     */
    public interface Source {

        default boolean hasValue() {
            return this instanceof Component component && anySet(component);
        }

        default boolean hasError() {
            return this instanceof Component component && anyInvalid(component);
        }
    }

    /** Drive {@code dot} from a tab's own indicator state. */
    public static void apply(Span dot, Source source) {
        apply(dot, source.hasValue(), source.hasError());
    }

    /** A hidden dot for a tab label. {@code tooltip} explains what the dot means on hover. */
    public static Span dot(String tooltip) {
        final Span dot = new Span();
        dot.addClassName("tab-indicator");
        dot.setVisible(false);
        if (tooltip != null && !tooltip.isBlank()) {
            dot.getElement().setAttribute("title", tooltip);
        }
        return dot;
    }

    /**
     * Show {@code dot} when the tab holds a value or has an invalid field, and
     * colour it red while a field is invalid.
     */
    public static void apply(Span dot, boolean hasValue, boolean invalid) {
        dot.setVisible(hasValue || invalid);
        dot.getElement().getClassList().set(ERROR_CLASS, invalid);
    }

    /** A field counts as set when it holds a non-empty, non-zero value. */
    public static boolean isSet(HasValue<?, ?> field) {
        final Object value = field.getValue();
        if (value == null) {
            return false;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0d;
        }
        if (value instanceof String text) {
            return !text.isBlank();
        }
        return !field.isEmpty();
    }

    /** True if any field anywhere under {@code root} holds a non-empty, non-zero value. */
    public static boolean anySet(Component root) {
        return root.getChildren().anyMatch(child ->
                (child instanceof HasValue<?, ?> field && isSet(field)) || anySet(child));
    }

    /** True if any field anywhere under {@code root} is currently invalid. */
    public static boolean anyInvalid(Component root) {
        return root.getChildren().anyMatch(child ->
                (child instanceof HasValidation validation && validation.isInvalid()) || anyInvalid(child));
    }
}
