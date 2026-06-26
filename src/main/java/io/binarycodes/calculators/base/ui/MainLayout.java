package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.menu.MenuConfiguration;
import com.vaadin.flow.server.menu.MenuEntry;
import io.binarycodes.calculators.base.prefs.UserPreferences;

/**
 * App-wide layout: title-bar (brand + preferences) and a left drawer with a
 * SideNav populated automatically from {@code @Menu}-annotated views.
 */
@Layout
@CssImport(value = "./shadow/input-error-message.css", themeFor = "vaadin-number-field")
@CssImport(value = "./shadow/input-error-message.css", themeFor = "vaadin-integer-field")
@CssImport(value = "./shadow/input-error-message.css", themeFor = "vaadin-custom-field")
public class MainLayout extends AppLayout {

    private static final String VAADIN_URL = "https://vaadin.com";
    private static final String GITHUB_URL = "https://github.com/binarycodes/calculators";

    public MainLayout(UserPreferences prefs) {
        setPrimarySection(Section.DRAWER);

        // The Scroller grows to fill the drawer so the footer is pinned to the bottom.
        final Scroller navScroller = new Scroller(buildSideNav());
        navScroller.getStyle().setFlexGrow("1");
        addToDrawer(buildBrand(), navScroller, buildDrawerFooter());

        addToNavbar(buildHeader(prefs));

        // Load prefs from localStorage on first render and re-apply on each nav.
        prefs.loadFromBrowser(null);
    }

    private RouterLink buildBrand() {
        final H1 brand = new H1(getTranslation("app.name"));
        brand.getStyle()
                .setFontSize("var(--vaadin-font-size-xl, 1.25rem)")
                .setMargin("var(--vaadin-padding-m, 0.75rem)")
                .setFontWeight("700");

        // The brand doubles as the link home, replacing the old "Home" menu entry.
        final RouterLink home = new RouterLink();
        home.setRoute(LandingView.class);
        home.add(brand);
        home.addClassName("brand-link");
        return home;
    }

    private SideNav buildSideNav() {
        final SideNav nav = new SideNav();
        // Auto-discover routes annotated with @Menu. Falls back to a hard-coded
        // entry until the first view is added so the drawer is not empty.
        final var entries = MenuConfiguration.getMenuEntries();
        if (entries.isEmpty()) {
            nav.addItem(new SideNavItem(getTranslation("nav.welcome"), "/", VaadinIcon.HOME.create()));
        } else {
            entries.forEach(entry -> nav.addItem(toSideNavItem(entry)));
        }
        return nav;
    }

    private SideNavItem toSideNavItem(MenuEntry entry) {
        final SideNavItem item = new SideNavItem(MenuTitles.titleFor(entry), entry.path());
        if (entry.icon() != null && !entry.icon().isBlank()) {
            // Vaadin icon class string like "vaadin:piggy-bank" or "lumo:cog".
            try {
                final String[] parts = entry.icon().split(":", 2);
                if (parts.length == 2 && "vaadin".equalsIgnoreCase(parts[0])) {
                    final VaadinIcon vi = VaadinIcon.valueOf(parts[1].toUpperCase().replace('-', '_'));
                    item.setPrefixComponent(vi.create());
                }
            } catch (final Exception ignore) { /* fall through to no icon */ }
        }
        return item;
    }

    private HorizontalLayout buildDrawerFooter() {
        final Anchor vaadin = footerLink(VAADIN_URL, getTranslation("footer.vaadin"), VaadinIcon.VAADIN_H.create());
        final Anchor github = footerLink(GITHUB_URL, getTranslation("footer.github"), githubIcon());

        final HorizontalLayout footer = new HorizontalLayout(vaadin, github);
        footer.addClassName("drawer-footer");
        footer.setAlignItems(FlexComponent.Alignment.CENTER);
        return footer;
    }

    private Anchor footerLink(String href, String label, Component icon) {
        final Anchor link = new Anchor(href, icon);
        link.setTarget("_blank");
        link.addClassName("drawer-footer-link");
        // No visible text, so expose the destination to assistive tech and as a tooltip.
        link.getElement().setAttribute("aria-label", label);
        link.setTitle(label);
        return link;
    }

    // VaadinIcon has no GitHub mark, so embed the official Octocat glyph inline.
    private Component githubIcon() {
        return new Html("""
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" width="20" height="20" \
                fill="currentColor" aria-hidden="true">\
                <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 \
                0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 \
                1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 \
                0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 \
                2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 \
                1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 \
                2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8z"/></svg>""");
    }

    private HorizontalLayout buildHeader(UserPreferences prefs) {
        final DrawerToggle toggle = new DrawerToggle();
        final HorizontalLayout header = new HorizontalLayout(toggle, new PreferencesBar(prefs));
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.getStyle().setPaddingRight("var(--vaadin-padding-m, 1rem)");
        return header;
    }
}
