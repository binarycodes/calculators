package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.router.Route;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every calculator tile on the landing page must carry a short description and a
 * menu title. Blurbs and titles now live in the translation bundle, so this
 * scans for all {@code @Route} views (a newly added calculator is covered
 * automatically) and asserts each route has a non-blank {@code landing.blurb.*}
 * and {@code menu.*} entry — the landing-only empty route ("") is the exception.
 */
class LandingViewBlurbsTest {

    @Test
    void every_calculator_route_has_a_blurb_and_menu_title() throws Exception {
        final ResourceBundle bundle = ResourceBundle.getBundle("vaadin-i18n/translations", Locale.UK);

        final var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Route.class));
        final Set<?> candidates = scanner.findCandidateComponents("io.binarycodes.calculators");

        for (final Object candidate : candidates) {
            final String className = ((org.springframework.beans.factory.config.BeanDefinition) candidate)
                    .getBeanClassName();
            final Class<?> viewClass = Class.forName(className);
            final String path = viewClass.getAnnotation(Route.class).value();
            if (path.isBlank()) {
                continue; // the landing page itself carries no tile
            }
            assertNonBlankKey(bundle, "landing.blurb." + path, viewClass.getSimpleName(), "description");
            assertNonBlankKey(bundle, "menu." + path, viewClass.getSimpleName(), "menu title");
        }
    }

    private static void assertNonBlankKey(ResourceBundle bundle, String key, String view, String what) {
        assertTrue(bundle.containsKey(key),
                "Route view " + view + " is missing translation key '" + key + "' (" + what + ")");
        assertFalse(bundle.getString(key).isBlank(),
                "Translation key '" + key + "' for " + view + " (" + what + ") is blank");
    }
}
