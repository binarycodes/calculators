package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import io.binarycodes.calculators.base.config.BuildInfo;

/**
 * Tiny drawer-footer caption showing the deployed commit. Displays only the
 * short SHA — as a link to the commit when one is available — with the build
 * timestamp tucked into the hover tooltip. Renders nothing when there is no
 * build metadata (a plain dev run).
 */
public class VersionTag extends Span {

    public VersionTag(BuildInfo buildInfo) {
        addClassName("drawer-footer-version");

        if (!buildInfo.hasVersion()) {
            return;
        }

        final String commitUrl = buildInfo.commitUrl();
        if (commitUrl.isEmpty()) {
            add(new Span(buildInfo.shortSha()));
        } else {
            final Anchor commitLink = new Anchor(commitUrl, buildInfo.shortSha());
            commitLink.setTarget("_blank");
            commitLink.addClassName("drawer-footer-version-link");
            add(commitLink);
        }

        final String buildTime = buildInfo.buildTime();
        if (!buildTime.isEmpty()) {
            setTitle(getTranslation("footer.version.builtOn", buildTime));
        }
    }
}
