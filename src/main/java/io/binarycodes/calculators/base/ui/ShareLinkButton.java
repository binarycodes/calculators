package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.webshare.ShareContent;
import com.vaadin.flow.component.webshare.WebShare;
import com.vaadin.flow.component.webshare.WebShareSupport;

/**
 * Share control for a calculator scenario: two buttons, exactly one visible
 * depending on Vaadin's {@link WebShare#supportSignal() Web Share support signal}.
 *
 * <ul>
 *   <li><b>Share</b> — visible when sharing is {@code SUPPORTED}; opens the
 *       native OS share sheet (mobile, Safari, Edge, all iOS browsers).</li>
 *   <li><b>Copy link</b> — visible otherwise; copies the link to the clipboard.</li>
 * </ul>
 *
 * <p>Binding visibility to the signal also triggers the browser support
 * handshake. The absolute link is assembled on the client from the live
 * {@code window.location} at click time — a server-captured base goes stale
 * across single-page navigation (it would point at the previously-viewed
 * calculator). The clipboard write runs inside the click gesture (the Clipboard
 * API rejects async writes), and the URL is absolute — iOS WebKit rejects
 * relative URLs in {@code navigator.share}.
 */
public class ShareLinkButton extends Composite<HorizontalLayout> {

    private final TextField shareUrl = new TextField();
    private final Button shareButton = new Button(getTranslation("share.share"), VaadinIcon.CONNECT.create());
    private final Button copyButton = new Button(getTranslation("share.copy"), VaadinIcon.COPY.create());

    private boolean visibilityBound;

    public ShareLinkButton(String shareTitle) {
        this.shareButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        this.copyButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        this.shareButton.setTooltipText(getTranslation("share.tooltip.share"));
        this.copyButton.setTooltipText(getTranslation("share.tooltip.copy"));

        // Hidden via CSS, not setVisible(false): Flow withholds property syncs
        // from invisible components, which would leave the share URL empty.
        this.shareUrl.getStyle().set("display", "none");
        this.shareUrl.getElement().setAttribute("aria-hidden", "true");

        // Native share sheet, fed the current absolute link within the gesture.
        WebShare.onClick(this.shareButton).share(ShareContent.create()
                .title(shareTitle)
                .url(this.shareUrl));

        // Copy runs inside the click gesture; the link is built from the live
        // location and the current token. The server listener confirms.
        this.copyButton.getElement().executeJs(
                "this.addEventListener('click', () => {"
                        + "  const token = this.shareToken;"
                        + "  if (token && navigator.clipboard) {"
                        + "    navigator.clipboard.writeText("
                        + "      location.origin + location.pathname + '?s=' + token).catch(() => {});"
                        + "  }"
                        + "});");
        this.copyButton.addClickListener(event ->
                Notification.show(getTranslation("share.copied"), 2000, Notification.Position.BOTTOM_START));

        final HorizontalLayout content = getContent();
        content.addClassName("share-link-button");
        content.setPadding(false);
        content.setSpacing(false);
        content.add(this.shareUrl, this.shareButton, this.copyButton);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);

        // Show exactly one button, decided by the support signal. Bound here (not
        // in the constructor) because supportSignal() needs an active UI; binding
        // also triggers the browser support handshake. Bind once across re-attach.
        if (!this.visibilityBound) {
            this.visibilityBound = true;
            this.shareButton.bindVisible(WebShare.supportSignal()
                    .map(support -> support == WebShareSupport.SUPPORTED));
            this.copyButton.bindVisible(WebShare.supportSignal()
                    .map(support -> support != WebShareSupport.SUPPORTED));
        }
    }

    /** Update the token a click shares (or copies), keeping the link current. */
    public void setToken(String token) {
        final String safeToken = token == null ? "" : token;
        // The copy handler reads this property and assembles the URL on the client.
        this.copyButton.getElement().setProperty("shareToken", safeToken);
        // Web Share reads the field value; build the absolute link from the live
        // location so it points at the current view, not the one captured at attach.
        this.shareUrl.getElement().executeJs(
                "this.value = $0 ? location.origin + location.pathname + '?s=' + $0 : '';", safeToken);
    }

    /** The token a click currently copies/shares; for tests. */
    String shareTokenProperty() {
        return this.copyButton.getElement().getProperty("shareToken");
    }
}
