package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;

/**
 * "Share" button that copies a shareable scenario URL to the clipboard.
 *
 * <p>The clipboard write must happen synchronously inside the click gesture: the
 * Clipboard API rejects writes made from an asynchronous server round-trip
 * ("Document is not focused" / lost user activation). So the copy runs entirely
 * client-side in a DOM click listener, reading a {@code shareToken} property the
 * server keeps current via {@link #setToken}. A separate server-side click
 * listener shows the confirmation toast.
 */
public class ShareLinkButton extends Button {

    public ShareLinkButton() {
        super("Share", VaadinIcon.CONNECT.create());
        addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        setTooltipText("Copy a link to this scenario");

        // Client-side copy, within the user gesture, from the pre-set token.
        getElement().executeJs(
                "this.addEventListener('click', () => {"
                        + "  const token = this.shareToken;"
                        + "  if (!token) { return; }"
                        + "  const url = location.origin + location.pathname + '?s=' + token;"
                        + "  if (navigator.clipboard) { navigator.clipboard.writeText(url).catch(() => {}); }"
                        + "});");
        addClickListener(event ->
                Notification.show("Link copied", 2000, Notification.Position.BOTTOM_START));
    }

    /** Set the token the next click will turn into a {@code ?s=} URL and copy. */
    public void setToken(String token) {
        getElement().setProperty("shareToken", token);
    }
}
