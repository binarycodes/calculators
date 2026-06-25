package io.binarycodes.calculators.base.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The Web Share URL must track the latest scenario token: {@link
 * ShareLinkButton#setToken} feeds the reactive field the share sheet reads, so a
 * regression here would silently share a stale link. (Before attach there's no
 * resolved origin, so the link is the relative {@code ?s=…}.)
 */
class ShareLinkButtonTest {

    @Test
    void set_token_keeps_the_share_url_current() {
        final ShareLinkButton button = new ShareLinkButton("Buy vs Rent Calculator");

        button.setToken("FIRST");
        assertEquals("?s=FIRST", button.shareUrlValue());

        // A later recalculation must overwrite the link, not leave the old one.
        button.setToken("SECOND");
        assertEquals("?s=SECOND", button.shareUrlValue());
    }
}
