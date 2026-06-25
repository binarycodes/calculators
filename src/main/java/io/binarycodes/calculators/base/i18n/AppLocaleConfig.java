package io.binarycodes.calculators.base.i18n;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Pins every session to en_GB. The app ships only the en_GB bundle for now and
 * deliberately does not detect or offer other languages; fixing the locale keeps
 * formatting and translation deterministic regardless of the browser's
 * {@code Accept-Language}. Future multi-language support would replace this with
 * locale negotiation.
 */
@Component
public class AppLocaleConfig implements VaadinServiceInitListener {

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addSessionInitListener(sessionInit ->
                sessionInit.getSession().setLocale(Locale.UK));
    }
}
