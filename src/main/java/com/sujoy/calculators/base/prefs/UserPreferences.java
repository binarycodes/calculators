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
    static final int FONT_MIN = 12, FONT_MAX = 22, FONT_STEP = 2, FONT_DEFAULT = 16;

    private Currency currency = Currency.INR;
    private Theme    theme    = Theme.LIGHT;
    private int      fontPx   = FONT_DEFAULT;
    private boolean  loaded   = false;

    private final List<Consumer<UserPreferences>> listeners = new ArrayList<>();

    public Currency currency() { return currency; }
    public Theme    theme()    { return theme; }
    public int      fontPx()   { return fontPx; }

    public void setCurrency(Currency c) {
        if (c == null || c == currency) return;
        currency = c;
        notifyAndPersist();
    }

    public void setTheme(Theme t) {
        if (t == null || t == theme) return;
        theme = t;
        notifyAndPersist();
    }

    public void incFont() { setFontPx(Math.min(FONT_MAX, fontPx + FONT_STEP)); }
    public void decFont() { setFontPx(Math.max(FONT_MIN, fontPx - FONT_STEP)); }
    public void resetFont() { setFontPx(FONT_DEFAULT); }

    private void setFontPx(int px) {
        if (px == fontPx) return;
        fontPx = px;
        notifyAndPersist();
    }

    public void addChangeListener(Consumer<UserPreferences> l) {
        listeners.add(l);
    }

    /**
     * Load prefs from browser localStorage. Must be called from an active UI
     * context (e.g. inside {@code AfterNavigationEvent}, {@code UI.access},
     * or an attach listener). Idempotent.
     */
    public void loadFromBrowser(Runnable onLoaded) {
        if (loaded) {
            if (onLoaded != null) onLoaded.run();
            return;
        }
        UI ui = UI.getCurrent();
        if (ui == null) {
            loaded = true;
            if (onLoaded != null) onLoaded.run();
            return;
        }
        WebStorage.getItem(STORAGE_KEY, raw -> {
            if (raw != null && !raw.isBlank()) parseJson(raw);
            loaded = true;
            // Apply to client (theme class / font size) and notify view listeners.
            applyToClient();
            listeners.forEach(l -> l.accept(this));
            if (onLoaded != null) onLoaded.run();
        });
    }

    /** Apply theme class + font size to the document via JS. */
    public void applyToClient() {
        UI ui = UI.getCurrent();
        if (ui == null) return;
        ui.getPage().executeJs(
                "document.documentElement.classList.toggle('dark', $0);" +
                "document.documentElement.style.fontSize = $1 + 'px';",
                theme == Theme.DARK, fontPx);
    }

    private void notifyAndPersist() {
        applyToClient();
        listeners.forEach(l -> l.accept(this));
        persist();
    }

    private void persist() {
        UI ui = UI.getCurrent();
        if (ui == null) return;
        String json = "{\"currency\":\"" + currency.name() +
                "\",\"theme\":\""        + theme.name().toLowerCase() +
                "\",\"fontSize\":"       + fontPx + "}";
        WebStorage.setItem(STORAGE_KEY, json);
    }

    private void parseJson(String raw) {
        // Lightweight parse: no Jackson dep needed for three known fields.
        String curMatch = match(raw, "\"currency\"\\s*:\\s*\"([A-Z]+)\"");
        if (curMatch != null) try { currency = Currency.valueOf(curMatch); } catch (Exception ignore) {}
        String themeMatch = match(raw, "\"theme\"\\s*:\\s*\"(\\w+)\"");
        if (themeMatch != null) theme = "dark".equalsIgnoreCase(themeMatch) ? Theme.DARK : Theme.LIGHT;
        String fontMatch = match(raw, "\"fontSize\"\\s*:\\s*(\\d+)");
        if (fontMatch != null) try { fontPx = Math.max(FONT_MIN, Math.min(FONT_MAX, Integer.parseInt(fontMatch))); } catch (Exception ignore) {}
    }

    private static String match(String s, String regex) {
        var m = java.util.regex.Pattern.compile(regex).matcher(s);
        return m.find() ? m.group(1) : null;
    }
}
