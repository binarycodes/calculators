package io.binarycodes.calculators.base.i18n;

import com.vaadin.flow.component.UI;

/**
 * Resolves a translation key in a static (non-{@link com.vaadin.flow.component.Component})
 * context — e.g. factory helpers and grid renderers. Components should prefer
 * their own {@code getTranslation(...)}; this exists for the few places without a
 * component instance to hand. Resolution goes through the current {@link UI},
 * which carries the session locale set in {@link AppLocaleConfig}.
 */
public final class Translations {

    private Translations() {
    }

    public static String get(String key, Object... params) {
        final UI ui = UI.getCurrent();
        // Outside a UI thread there is no locale to resolve against; returning the
        // key keeps the failure visible rather than throwing in a renderer.
        return ui == null ? key : ui.getTranslation(key, params);
    }
}
