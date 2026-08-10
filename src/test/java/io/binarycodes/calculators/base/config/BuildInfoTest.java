package io.binarycodes.calculators.base.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;

import java.time.Instant;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildInfoTest {

    private static final String FULL_SHA = "abcdef1234567890abcdef1234567890abcdef12";
    private static final Instant BUILT_AT = Instant.parse("2026-08-10T14:32:00Z");

    @Test
    void short_sha_is_the_first_seven_characters_of_the_commit() {
        final BuildInfo info = new BuildInfo(providerOf(buildProperties(FULL_SHA, BUILT_AT)), gitHubLinks());
        assertEquals("abcdef1", info.shortSha());
    }

    @Test
    void commit_url_fills_the_sha_placeholder_with_the_full_commit() {
        final BuildInfo info = new BuildInfo(providerOf(buildProperties(FULL_SHA, BUILT_AT)), gitHubLinks());
        assertEquals("https://github.com/binarycodes/calculators/commit/" + FULL_SHA, info.commitUrl());
    }

    @Test
    void commit_url_uses_the_configured_template_not_a_hardcoded_github_path() {
        final AppLinks gitLab = new AppLinks("https://vaadin.com",
                "https://gitlab.example/team/app",
                "https://gitlab.example/team/app/-/commit/{sha}");
        final BuildInfo info = new BuildInfo(providerOf(buildProperties(FULL_SHA, BUILT_AT)), gitLab);
        assertEquals("https://gitlab.example/team/app/-/commit/" + FULL_SHA, info.commitUrl());
    }

    @Test
    void build_time_is_formatted_as_utc_date_and_time() {
        final BuildInfo info = new BuildInfo(providerOf(buildProperties(FULL_SHA, BUILT_AT)), gitHubLinks());
        assertEquals("2026-08-10 14:32 UTC", info.buildTime());
    }

    @Test
    void an_unknown_commit_falls_back_to_dev_with_no_link_but_still_reports_a_build() {
        final BuildInfo info = new BuildInfo(providerOf(buildProperties("unknown", BUILT_AT)), gitHubLinks());
        assertEquals("dev", info.shortSha());
        assertEquals("", info.commitUrl());
        assertEquals("2026-08-10 14:32 UTC", info.buildTime());
        assertTrue(info.hasVersion());
    }

    @Test
    void an_absent_build_properties_bean_shows_nothing() {
        final BuildInfo info = new BuildInfo(providerOf(null), gitHubLinks());
        assertEquals("dev", info.shortSha());
        assertEquals("", info.commitUrl());
        assertEquals("", info.buildTime());
        assertFalse(info.hasVersion());
    }

    private static AppLinks gitHubLinks() {
        return new AppLinks("https://vaadin.com",
                "https://github.com/binarycodes/calculators",
                "https://github.com/binarycodes/calculators/commit/{sha}");
    }

    private static BuildProperties buildProperties(String commit, Instant builtAt) {
        final Properties properties = new Properties();
        properties.setProperty("commit", commit);
        properties.setProperty("time", String.valueOf(builtAt.toEpochMilli()));
        return new BuildProperties(properties);
    }

    private static ObjectProvider<BuildProperties> providerOf(BuildProperties value) {
        return new ObjectProvider<>() {
            @Override
            public BuildProperties getObject() {
                if (value == null) {
                    throw new NoSuchBeanDefinitionException(BuildProperties.class);
                }
                return value;
            }

            @Override
            public BuildProperties getObject(Object... args) {
                return getObject();
            }

            @Override
            public BuildProperties getIfAvailable() {
                return value;
            }

            @Override
            public BuildProperties getIfUnique() {
                return value;
            }
        };
    }
}
