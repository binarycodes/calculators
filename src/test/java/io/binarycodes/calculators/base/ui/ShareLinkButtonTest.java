package io.binarycodes.calculators.base.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The Web Share content and the clipboard fallback must both track the latest
 * scenario token. {@link ShareLinkButton#setToken} feeds the reactive URL field
 * the share sheet reads and the {@code shareToken} property the clipboard
 * fallback reads — a regression in either silently shares a stale link.
 */
class ShareLinkButtonTest {

    @Test
    void set_token_updates_both_the_web_share_url_and_the_clipboard_token() {
        final ShareLinkButton button = new ShareLinkButton("Buy vs Rent Calculator");

        button.setToken("FIRST");
        assertEquals("?s=FIRST", button.shareUrlValue());
        assertEquals("FIRST", button.getElement().getProperty("shareToken"));

        // A later recalculation must overwrite the link, not leave the old one.
        button.setToken("SECOND");
        assertEquals("?s=SECOND", button.shareUrlValue());
        assertEquals("SECOND", button.getElement().getProperty("shareToken"));
    }
}
