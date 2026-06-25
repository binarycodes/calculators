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
 * handshake. The clipboard write runs inside the click gesture (the Clipboard
 * API rejects async writes), and the shared URL is absolute — iOS WebKit rejects
 * relative URLs in {@code navigator.share}.
 */
public class ShareLinkButton extends Composite<HorizontalLayout> {

    // Reactive source for the Web Share URL, read on the client within the
    // gesture; kept in sync with the current scenario link.
    private final TextField shareUrl = new TextField();
    private final Button shareButton = new Button("Share", VaadinIcon.CONNECT.create());
    private final Button copyButton = new Button("Copy link", VaadinIcon.COPY.create());

    private String baseUrl;       // absolute origin + path, resolved on attach
    private String currentToken;  // latest scenario token
    private boolean visibilityBound;

    public ShareLinkButton(String shareTitle) {
        this.shareButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        this.copyButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        this.shareButton.setTooltipText("Share a link to this scenario");
        this.copyButton.setTooltipText("Copy a link to this scenario");

        // Hidden via CSS, not setVisible(false): Flow withholds property syncs
        // from invisible components, which would leave the share URL empty.
        this.shareUrl.getStyle().set("display", "none");
        this.shareUrl.getElement().setAttribute("aria-hidden", "true");

        // Native share sheet, fed the current absolute link within the gesture.
        WebShare.onClick(this.shareButton).share(ShareContent.create()
                .title(shareTitle)
                .url(this.shareUrl));

        // Copy runs inside the click gesture; the server-side listener confirms.
        this.copyButton.getElement().executeJs(
                "this.addEventListener('click', () => {"
                        + "  const url = this.shareAbsoluteUrl;"
                        + "  if (url && navigator.clipboard) { navigator.clipboard.writeText(url).catch(() => {}); }"
                        + "});");
        this.copyButton.addClickListener(event ->
                Notification.show("Link copied", 2000, Notification.Position.BOTTOM_START));

        final HorizontalLayout content = getContent();
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

        // Resolve the page origin + path once so both buttons use an absolute link.
        attachEvent.getUI().getPage().fetchCurrentURL(url -> {
            this.baseUrl = url.getProtocol() + "://" + url.getAuthority() + url.getPath();
            refreshLink();
        });
    }

    /** Update the token a click shares (or copies), keeping the link current. */
    public void setToken(String token) {
        this.currentToken = token;
        refreshLink();
    }

    private void refreshLink() {
        if (this.currentToken == null) {
            return;
        }
        final String link = (this.baseUrl == null ? "" : this.baseUrl) + "?s=" + this.currentToken;
        this.shareUrl.setValue(link);
        this.copyButton.getElement().setProperty("shareAbsoluteUrl", link);
    }

    /** The URL the Web Share content currently points at; for tests. */
    String shareUrlValue() {
        return this.shareUrl.getValue();
    }
}
