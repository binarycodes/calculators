package com.sujoy.calculators.base.ui;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.menu.MenuConfiguration;

/** Landing page lists every menu-annotated view as a quick link. */
@Route("")
@PageTitle("Calculators")
public class WelcomeView extends VerticalLayout {

    public WelcomeView() {
        setPadding(true);
        setSpacing(true);

        add(new H2("Calculators"));
        add(new Paragraph("Pick a calculator from the side navigation."));

        var entries = MenuConfiguration.getMenuEntries();
        if (entries.isEmpty()) {
            add(new Paragraph("No calculators registered yet."));
            return;
        }
        for (var entry : entries) {
            com.vaadin.flow.component.html.Anchor a =
                    new com.vaadin.flow.component.html.Anchor(entry.path(), entry.title());
            a.getStyle().setDisplay(com.vaadin.flow.dom.Style.Display.BLOCK);
            add(a);
        }
    }
}
