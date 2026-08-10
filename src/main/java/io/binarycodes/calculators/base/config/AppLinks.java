package io.binarycodes.calculators.base.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * External links surfaced in the UI (currently the drawer footer), configurable
 * via {@code app.links.*} so a self-hoster can point them at their own fork.
 *
 * <p>{@code githubCommitUrl} is a template with a {@code {sha}} placeholder that
 * the version tag fills in with the deployed commit; a self-hoster on another
 * forge overrides it (e.g. a GitLab {@code /-/commit/} path).
 */
@ConfigurationProperties("app.links")
public record AppLinks(
        @DefaultValue("https://vaadin.com") String vaadin,
        @DefaultValue("https://github.com/binarycodes/calculators") String github,
        @DefaultValue("https://github.com/binarycodes/calculators/commit/{sha}") String githubCommitUrl) {
}
