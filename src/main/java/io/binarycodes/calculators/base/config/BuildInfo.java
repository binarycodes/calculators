package io.binarycodes.calculators.base.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Resolves the deployed build's identity — short commit SHA, a link to that
 * commit, and the build timestamp — for display in the drawer footer.
 *
 * <p>Values come from Spring's {@link BuildProperties} bean, populated at build
 * time from {@code build-info.properties} (the commit is stamped in via the
 * {@code build.commit} Maven property). The bean is absent for plain dev runs
 * that skip {@code build-info}, so it is injected optionally and everything
 * degrades to a {@code "dev"} label with no link.
 */
@Component
public class BuildInfo {

    static final String SHA_PLACEHOLDER = "{sha}";

    private static final String UNKNOWN_COMMIT = "unknown";
    private static final String DEV_LABEL = "dev";
    private static final int SHORT_SHA_LENGTH = 7;
    private static final DateTimeFormatter BUILD_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'", Locale.ROOT).withZone(ZoneOffset.UTC);

    private final String commit;
    private final String buildTime;
    private final String commitUrlTemplate;

    public BuildInfo(ObjectProvider<BuildProperties> buildProperties, AppLinks links) {
        final BuildProperties properties = buildProperties.getIfAvailable();
        this.commit = resolveCommit(properties);
        this.buildTime = resolveBuildTime(properties);
        this.commitUrlTemplate = links.githubCommitUrl();
    }

    /** Whether there is any build metadata to show (a {@link BuildProperties} bean was present). */
    public boolean hasVersion() {
        return !commit.isEmpty() || !buildTime.isEmpty();
    }

    /** The seven-character abbreviated commit, or {@code "dev"} when the commit is unknown. */
    public String shortSha() {
        if (commit.isEmpty()) {
            return DEV_LABEL;
        }
        return commit.length() > SHORT_SHA_LENGTH ? commit.substring(0, SHORT_SHA_LENGTH) : commit;
    }

    /** The commit link built from the configured template, or empty when the commit is unknown. */
    public String commitUrl() {
        if (commit.isEmpty() || !commitUrlTemplate.contains(SHA_PLACEHOLDER)) {
            return "";
        }
        return commitUrlTemplate.replace(SHA_PLACEHOLDER, commit);
    }

    /** The build timestamp as {@code yyyy-MM-dd HH:mm 'UTC'}, or empty when unavailable. */
    public String buildTime() {
        return buildTime;
    }

    private static String resolveCommit(BuildProperties properties) {
        if (properties == null) {
            return "";
        }
        final String value = properties.get("commit");
        if (value == null || value.isBlank() || UNKNOWN_COMMIT.equals(value)) {
            return "";
        }
        return value;
    }

    private static String resolveBuildTime(BuildProperties properties) {
        if (properties == null || properties.getTime() == null) {
            return "";
        }
        return BUILD_TIME_FORMAT.format(properties.getTime());
    }
}
