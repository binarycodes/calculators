package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.radiobutton.RadioGroupVariant;
import com.vaadin.flow.component.shared.Tooltip;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import io.binarycodes.calculators.base.money.SupportedCurrency;
import io.binarycodes.calculators.base.prefs.FontSize;
import io.binarycodes.calculators.base.prefs.Theme;
import io.binarycodes.calculators.base.prefs.UserPreferences;

/**
 * Header bar with currency / theme / font-size controls. Reads & writes via
 * {@link UserPreferences}.
 *
 * <p>The currency and font-size choosers are real {@link RadioButtonGroup}s so
 * they have correct radio semantics and keyboard navigation (arrow keys move
 * between options, Space selects). CSS in {@code segmented-toggle.css}
 * styles them visually as a button-group pill.</p>
 */
public class PreferencesBar extends HorizontalLayout {

    private final UserPreferences prefs;
    private final RadioButtonGroup<SupportedCurrency> currencyGroup = new RadioButtonGroup<>();
    private final RadioButtonGroup<FontSize> fontSizeGroup = new RadioButtonGroup<>();
    private final Button themeToggle = new Button();

    public PreferencesBar(UserPreferences prefs) {
        this.prefs = prefs;
        addClassName("preferences-bar");
        setSpacing(true);
        setAlignItems(FlexComponent.Alignment.CENTER);
        getStyle().setPaddingRight("var(--vaadin-padding-m, 1rem)");

        configureCurrencyGroup();
        configureFontSizeGroup();
        configureThemeToggle();

        add(this.currencyGroup, this.fontSizeGroup, this.themeToggle);

        prefs.addChangeListener(p -> syncFromPrefs());
        syncFromPrefs();
    }

    private void configureCurrencyGroup() {
        this.currencyGroup.setItems(SupportedCurrency.values());
        // Renderer drives the visible content (symbol only); aria-label per item
        // is set so screen readers announce the full name.
        this.currencyGroup.setRenderer(new ComponentRenderer<>(c -> {
            final Span s = new Span(c.symbol());
            s.getElement().setAttribute("aria-label", c.name());
            return s;
        }));
        this.currencyGroup.addThemeVariants(RadioGroupVariant.AURA_HORIZONTAL);
        this.currencyGroup.addClassNames("segmented-toggle", "currency-toggle");
        this.currencyGroup.setAriaLabel("Currency");
        this.currencyGroup.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                this.prefs.setCurrency(e.getValue());
            }
        });
    }

    private void configureFontSizeGroup() {
        this.fontSizeGroup.setItems(FontSize.values());
        // Each item renders as a single sized "A". The accessible name comes
        // from aria-label so screen readers announce "Small text" etc.
        this.fontSizeGroup.setRenderer(new ComponentRenderer<>(size -> {
            final Span a = new Span("A");
            a.addClassNames("font-size-glyph", "font-size-" + size.name().toLowerCase());
            a.getElement().setAttribute("aria-label", size.accessibleName());
            return a;
        }));
        this.fontSizeGroup.addThemeVariants(RadioGroupVariant.AURA_HORIZONTAL);
        this.fontSizeGroup.addClassNames("segmented-toggle", "font-size-toggle");
        this.fontSizeGroup.setAriaLabel("Text size");
        this.fontSizeGroup.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                this.prefs.setFontSize(e.getValue());
            }
        });
    }

    private void configureThemeToggle() {
        this.themeToggle.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Tooltip.forComponent(this.themeToggle).setText("Toggle theme");
        this.themeToggle.addClickListener(e ->
                this.prefs.setTheme(this.prefs.theme() == Theme.DARK ? Theme.LIGHT : Theme.DARK));
    }

    private void syncFromPrefs() {
        this.currencyGroup.setValue(this.prefs.currency());
        this.fontSizeGroup.setValue(this.prefs.fontSize());
        this.themeToggle.setIcon(
                (this.prefs.theme() == Theme.DARK ? VaadinIcon.SUN_O : VaadinIcon.MOON_O).create());
    }
}
