package com.sujoy.calculators.base.ui;

import com.sujoy.calculators.base.money.Currency;
import com.sujoy.calculators.base.prefs.Theme;
import com.sujoy.calculators.base.prefs.UserPreferences;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.shared.Tooltip;

import java.util.EnumMap;
import java.util.Map;

/**
 * Header bar with currency / theme / font-size controls. Reads & writes via
 * {@link UserPreferences}.
 */
public class PreferencesBar extends HorizontalLayout {

    private final UserPreferences prefs;
    private final Map<Currency, Button> currencyButtons = new EnumMap<>(Currency.class);
    private final Button themeToggle = new Button();

    public PreferencesBar(UserPreferences prefs) {
        this.prefs = prefs;
        setSpacing(true);
        setAlignItems(FlexComponent.Alignment.CENTER);
        getStyle().setPaddingRight("var(--vaadin-padding-m, 1rem)");

        HorizontalLayout currencyGroup = buildCurrencyToggle();
        HorizontalLayout fontGroup = buildFontControls();
        configureThemeToggle();

        add(currencyGroup, fontGroup, themeToggle);

        prefs.addChangeListener(p -> syncFromPrefs());
        syncFromPrefs();
    }

    private HorizontalLayout buildCurrencyToggle() {
        HorizontalLayout group = new HorizontalLayout();
        group.addClassName("segmented-toggle");
        group.setSpacing(false);
        group.setPadding(false);
        for (Currency c : Currency.values()) {
            Button b = new Button(c.symbol());
            b.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            b.addClassName("currency-btn");
            Tooltip.forComponent(b).setText(c.name());
            b.addClickListener(e -> prefs.setCurrency(c));
            currencyButtons.put(c, b);
            group.add(b);
        }
        return group;
    }

    private HorizontalLayout buildFontControls() {
        Button dec   = fontSizeButton("A", "font-a-sm", "Decrease text size", e -> prefs.decFont());
        Button reset = fontSizeButton("A", "font-a-md", "Reset text size",    e -> prefs.resetFont());
        Button inc   = fontSizeButton("A", "font-a-lg", "Increase text size", e -> prefs.incFont());
        HorizontalLayout group = new HorizontalLayout(dec, reset, inc);
        group.addClassName("segmented-toggle");
        group.setSpacing(false);
        group.setPadding(false);
        return group;
    }

    private Button fontSizeButton(String text, String sizeClass, String tooltip,
                                  com.vaadin.flow.component.ComponentEventListener<com.vaadin.flow.component.ClickEvent<Button>> listener) {
        Button b = new Button(text);
        b.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        b.addClassNames("font-size-btn", sizeClass);
        Tooltip.forComponent(b).setText(tooltip);
        b.addClickListener(listener);
        return b;
    }

    private void configureThemeToggle() {
        themeToggle.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Tooltip.forComponent(themeToggle).setText("Toggle theme");
        themeToggle.addClickListener(e ->
                prefs.setTheme(prefs.theme() == Theme.DARK ? Theme.LIGHT : Theme.DARK));
    }

    private void syncFromPrefs() {
        Currency active = prefs.currency();
        currencyButtons.forEach((c, b) -> {
            boolean on = c == active;
            b.getElement().setAttribute("aria-pressed", Boolean.toString(on));
            b.removeClassName("active");
            if (on) b.addClassName("active");
        });
        themeToggle.setIcon((prefs.theme() == Theme.DARK ? VaadinIcon.SUN_O : VaadinIcon.MOON_O).create());
    }
}
