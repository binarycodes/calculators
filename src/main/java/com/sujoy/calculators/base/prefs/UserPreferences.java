package com.sujoy.calculators.base.prefs;

import com.sujoy.calculators.base.money.Currency;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.WebStorage;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Per-session user preferences (currency / theme / font size). Persisted to
 * browser localStorage under the key {@value #STORAGE_KEY} as compact JSON so
 * that they survive across sessions on the same browser.
 *
 * <p>Listeners registered via {@link #addChangeListener} are invoked on any
 * change. The bean is {@code VaadinSessionScope} so each browser session has
 * its own instance, but values are loaded from localStorage on first access.
 */
@Component
@VaadinSessionScope
public class UserPreferences {

    static final String STORAGE_KEY = "rc_prefs";

    private Currency currency = Currency.INR;
    private Theme    theme    = Theme.LIGHT;
    private FontSize fontSize = FontSize.MEDIUM;
    private boolean  loaded   = false;

    private final List<Consumer<UserPreferences>> listeners = new ArrayList<>();

    public Currency currency() { return this.currency; }
    public Theme    theme()    { return this.theme;    }
    public FontSize fontSize() { return this.fontSize; }

    public void setCurrency(Currency c) {
        if (c == null || c == this.currency) return;
        this.currency = c;
        notifyAndPersist();
    }

    public void setTheme(Theme t) {
        if (t == null || t == this.theme) return;
        this.theme = t;
        notifyAndPersist();
    }

    public void setFontSize(FontSize s) {
        if (s == null || s == this.fontSize) return;
        this.fontSize = s;
        notifyAndPersist();
    }

    public void addChangeListener(Consumer<UserPreferences> l) {
        this.listeners.add(l);
    }

    /**
     * Load prefs from browser localStorage. Must be called from an active UI
     * context (e.g. inside {@code AfterNavigationEvent}, {@code UI.access},
     * or an attach listener). Idempotent.
     */
    public void loadFromBrowser(Runnable onLoaded) {
        if (this.loaded) {
            if (onLoaded != null) onLoaded.run();
            return;
        }
        final UI ui = UI.getCurrent();
        if (ui == null) {
            this.loaded = true;
            if (onLoaded != null) onLoaded.run();
            return;
        }
        WebStorage.getItem(STORAGE_KEY, raw -> {
            if (raw != null && !raw.isBlank()) parseJson(raw);
            this.loaded = true;
            // Apply to client (theme class / font size) and notify view listeners.
            applyToClient();
            this.listeners.forEach(l -> l.accept(this));
            if (onLoaded != null) onLoaded.run();
        });
    }

    /** Apply theme class + font size to the document via JS. */
    public void applyToClient() {
        final UI ui = UI.getCurrent();
        if (ui == null) return;
        ui.getPage().executeJs(
                "document.documentElement.classList.toggle('dark', $0);" +
                "document.documentElement.style.fontSize = $1 + 'px';",
                this.theme == Theme.DARK, this.fontSize.px());
    }

    private void notifyAndPersist() {
        applyToClient();
        this.listeners.forEach(l -> l.accept(this));
        persist();
    }

    private void persist() {
        final UI ui = UI.getCurrent();
        if (ui == null) return;
        final String json = "{\"currency\":\"" + this.currency.name() +
                "\",\"theme\":\""    + this.theme.name().toLowerCase() +
                "\",\"fontSize\":\"" + this.fontSize.name() + "\"}";
        WebStorage.setItem(STORAGE_KEY, json);
    }

    private void parseJson(String raw) {
        // Lightweight parse: no Jackson dep needed for three known fields.
        final String curMatch = match(raw, "\"currency\"\\s*:\\s*\"([A-Z]+)\"");
        if (curMatch != null) try {
            this.currency = Currency.valueOf(curMatch);
        } catch (final Exception ignore) {}

        final String themeMatch = match(raw, "\"theme\"\\s*:\\s*\"(\\w+)\"");
        if (themeMatch != null) {
            this.theme = "dark".equalsIgnoreCase(themeMatch) ? Theme.DARK : Theme.LIGHT;
        }

        // Accept the new enum form ("MEDIUM") or, for backward compatibility, an
        // older numeric value ("16") — map nearest size via FontSize.fromPx.
        final String fontEnum = match(raw, "\"fontSize\"\\s*:\\s*\"(\\w+)\"");
        if (fontEnum != null) {
            try { this.fontSize = FontSize.valueOf(fontEnum); } catch (final Exception ignore) {}
        } else {
            final String fontInt = match(raw, "\"fontSize\"\\s*:\\s*(\\d+)");
            if (fontInt != null) try {
                this.fontSize = FontSize.fromPx(Integer.parseInt(fontInt));
            } catch (final Exception ignore) {}
        }
    }

    private static String match(String s, String regex) {
        final var m = java.util.regex.Pattern.compile(regex).matcher(s);
        return m.find() ? m.group(1) : null;
    }
}
