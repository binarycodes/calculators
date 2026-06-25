package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.server.menu.MenuEntry;
import io.binarycodes.calculators.base.i18n.Translations;

/**
 * Resolves a localized title for a {@code @Menu} entry. The {@code @Menu(title=…)}
 * annotation value is a compile-time constant and can't be translated directly,
 * so both the sidebar ({@link MainLayout}) and the landing tiles
 * ({@link LandingView}) look the title up by route path under the {@code menu.*}
 * keys, falling back to the annotation text for any entry without a key.
 */
final class MenuTitles {

    private MenuTitles() {
    }

    static String titleFor(MenuEntry entry) {
        final String path = stripLeadingSlash(entry.path());
        final String key = "menu." + path;
        final String translated = Translations.get(key);
        return translated.equals(key) ? entry.title() : translated;
    }

    static String stripLeadingSlash(String path) {
        if (path == null) {
            return "";
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }
}
