package io.binarycodes.calculators.base.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The copy/share control must track the latest scenario token: {@link
 * ShareLinkButton#setToken} pushes the token the client reads to assemble the
 * link, so a regression here would silently copy a stale one.
 */
class ShareLinkButtonTest {

    @Test
    void set_token_keeps_the_shared_token_current() {
        final ShareLinkButton button = new ShareLinkButton("Buy vs Rent Calculator");

        button.setToken("FIRST");
        assertEquals("FIRST", button.shareTokenProperty());

        // A later recalculation must overwrite the token, not leave the old one.
        button.setToken("SECOND");
        assertEquals("SECOND", button.shareTokenProperty());
    }
}
