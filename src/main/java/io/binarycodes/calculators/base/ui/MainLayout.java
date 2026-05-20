package io.binarycodes.calculators.base.ui;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.server.menu.MenuConfiguration;
import com.vaadin.flow.server.menu.MenuEntry;
import io.binarycodes.calculators.base.prefs.UserPreferences;

/**
 * App-wide layout: title-bar (brand + preferences) and a left drawer with a
 * SideNav populated automatically from {@code @Menu}-annotated views.
 */
@Layout
public class MainLayout extends AppLayout {

    public MainLayout(UserPreferences prefs) {
        setPrimarySection(Section.DRAWER);
        addToDrawer(buildBrand(), new Scroller(buildSideNav()));
        addToNavbar(buildHeader(prefs));

        // Load prefs from localStorage on first render and re-apply on each nav.
        prefs.loadFromBrowser(null);
    }

    private H1 buildBrand() {
        final H1 brand = new H1("Calculators");
        brand.getStyle()
                .setFontSize("var(--vaadin-font-size-xl, 1.25rem)")
                .setMargin("var(--vaadin-padding-m, 0.75rem)")
                .setFontWeight("700");
        return brand;
    }

    private SideNav buildSideNav() {
        final SideNav nav = new SideNav();
        // Auto-discover routes annotated with @Menu. Falls back to a hard-coded
        // entry until the first view is added so the drawer is not empty.
        final var entries = MenuConfiguration.getMenuEntries();
        if (entries.isEmpty()) {
            nav.addItem(new SideNavItem("Welcome", "/", VaadinIcon.HOME.create()));
        } else {
            entries.forEach(entry -> nav.addItem(toSideNavItem(entry)));
        }
        return nav;
    }

    private SideNavItem toSideNavItem(MenuEntry entry) {
        final SideNavItem item = new SideNavItem(entry.title(), entry.path());
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
