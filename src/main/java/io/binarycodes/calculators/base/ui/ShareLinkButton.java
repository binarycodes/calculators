package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.webshare.ShareContent;
import com.vaadin.flow.component.webshare.WebShare;

/**
 * "Share" button for a calculator scenario.
 *
 * <p>Where the browser supports the Web Share API (mobile, Safari, Edge, and
 * all iOS browsers) a click opens the native OS share sheet with a link to the
 * current scenario. The shared URL is read from a hidden field within the click
 * gesture, so it always reflects the latest inputs.
 *
 * <p>On browsers without {@code navigator.share} (most desktop Chrome / Firefox)
 * the click instead copies that link to the clipboard. The copy must run
 * synchronously inside the click gesture — the Clipboard API rejects writes made
 * from an asynchronous server round-trip — so it runs client-side from a
 * {@code shareToken} property the server keeps current via {@link #setToken}, and
 * confirms back through {@link #onLinkCopied()} only once the copy succeeds.
 */
public class ShareLinkButton extends Button {

    // Reactive source for the Web Share URL, kept in sync with the current
    // scenario token. ShareContent reads its value within the click gesture.
    private final TextField shareUrl = new TextField();

    public ShareLinkButton(String shareTitle) {
        super("Share", VaadinIcon.CONNECT.create());
        addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        setTooltipText("Share a link to this scenario");

        // Hidden via CSS, not setVisible(false): Flow withholds property syncs
        // from invisible components, which would leave the share URL empty.
        this.shareUrl.getStyle().set("display", "none");
        this.shareUrl.getElement().setAttribute("aria-hidden", "true");
        getElement().appendChild(this.shareUrl.getElement());

        // Native share sheet where supported. A relative URL is resolved by the
        // browser against the current page, so it needs no server-known origin.
        WebShare.onClick(this).share(ShareContent.create()
                .title(shareTitle)
                .url(this.shareUrl));

        // Fallback only where navigator.share is absent: copy the absolute link
        // to the clipboard within the click gesture, and confirm via the server
        // once — and only if — the copy actually succeeds.
        getElement().executeJs(
                "this.addEventListener('click', () => {"
                        + "  if (navigator.share) { return; }"
                        + "  const token = this.shareToken;"
                        + "  if (!token || !navigator.clipboard) { return; }"
                        + "  const url = location.origin + location.pathname + '?s=' + token;"
                        + "  navigator.clipboard.writeText(url)"
                        + "    .then(() => this.$server.onLinkCopied())"
                        + "    .catch(() => {});"
                        + "});");
    }

    /** Update the token a click shares (or copies), keeping the link current. */
    public void setToken(String token) {
        getElement().setProperty("shareToken", token);
        this.shareUrl.setValue("?s=" + token);
    }

    /** Confirmation toast for the clipboard fallback; invoked client-side after a successful copy. */
    @ClientCallable
    private void onLinkCopied() {
        Notification.show("Link copied", 2000, Notification.Position.BOTTOM_START);
    }

    /** The relative URL the Web Share content currently points at; for tests. */
    String shareUrlValue() {
        return this.shareUrl.getValue();
    }
}
