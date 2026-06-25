package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.router.Route;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Every calculator tile on the landing page must carry a short description.
 * Scans for all {@code @Route} views (so a newly added calculator is covered
 * automatically) and asserts each has a non-blank blurb — the landing-only
 * empty route ("") is the sole exception.
 */
class LandingViewBlurbsTest {

    @Test
    void every_calculator_route_has_a_non_blank_blurb() throws Exception {
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
            final String blurb = LandingView.blurbFor(path);
            assertFalse(blurb == null || blurb.isBlank(),
                    "Landing tile for route '" + path + "' (" + viewClass.getSimpleName()
                            + ") has no description — add a BLURBS entry in LandingView");
        }
    }
}
