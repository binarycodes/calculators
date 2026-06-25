package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.menu.MenuConfiguration;
import com.vaadin.flow.server.menu.MenuEntry;

import java.util.Map;

/**
 * Landing page that introduces the calculators in the app and lets the user
 * pick one. Tiles are populated from the {@code @Menu}-annotated routes so a
 * new calculator only needs an annotation to show up here — no edits to this
 * file required.
 */
@Route("")
@PageTitle("Calculators")
public class LandingView extends VerticalLayout {

    /**
     * One-line blurb per calculator path. The map is consulted by
     * {@link #blurbFor(String)} so tiles can carry a short description without
     * each view having to advertise it through {@code @Menu}.
     */
    private static final Map<String, String> BLURBS = Map.of(
            "retirement", "Project your retirement corpus through life expectancy with detailed cashflow modelling.",
            "goal", "Find the monthly investment needed to hit a post-tax goal by a given deadline.",
            "inflation", "See what an amount is worth at the other end of a horizon — forward or backward.",
            "investment", "Grow regular contributions through an invest-and-hold horizon; see maturity and real value.",
            "loan", "Work out the EMI for a loan, then see how prepayments cut the tenure or the monthly outgo.",
            "buyrent", "Compare buying a home against renting and investing the difference, with a break-even year."
    );

    public LandingView() {
        addClassName("landing-view");
        setWidthFull();
        setPadding(true);
        setSpacing(true);

        add(new H1("Calculators"));
        add(new Paragraph("Pick a calculator to get started."));

        // A responsive CSS grid — four tiles per row on desktop, collapsing to
        // one per row on mobile (see landing-view.css).
        final Div cards = new Div();
        cards.addClassName("landing-grid");

        for (final MenuEntry entry : MenuConfiguration.getMenuEntries()) {
            // Skip the entry that points back to this landing page.
            if (entry.menuClass() == LandingView.class) {
                continue;
            }
            cards.add(tile(entry));
        }
        add(cards);
    }

    private static Component tile(MenuEntry entry) {
        final Span iconWrapper = new Span(iconFor(entry.icon()));
        iconWrapper.addClassName("landing-tile-icon");

        final Span heading = new Span(entry.title());
        heading.addClassName("landing-tile-title");

        final Paragraph body = new Paragraph(blurbFor(entry.path()));
        body.addClassName("landing-tile-body");

        final VerticalLayout content = new VerticalLayout(iconWrapper, heading, body);
        content.setPadding(false);
        content.setSpacing(false);
        content.setAlignItems(FlexComponent.Alignment.START);

        final Card card = new Card();
        card.add(content);
        card.addClassName("landing-tile");
        card.setWidthFull();

        final RouterLink link = new RouterLink();
        link.setRoute(entry.menuClass());
        link.add(card);
        link.addClassName("landing-tile-link");
        return link;
    }

    private static Component iconFor(String iconName) {
        if (iconName == null || iconName.isBlank()) {
            return VaadinIcon.CIRCLE.create();
        }
        try {
            final String[] parts = iconName.split(":", 2);
            if (parts.length == 2 && "vaadin".equalsIgnoreCase(parts[0])) {
                return VaadinIcon.valueOf(parts[1].toUpperCase().replace('-', '_')).create();
            }
        } catch (final Exception ignored) { /* fall through */ }
        return VaadinIcon.CIRCLE.create();
    }

    static String blurbFor(String path) {
        final String key = path.startsWith("/") ? path.substring(1) : path;
        return BLURBS.getOrDefault(key, "");
    }
}
