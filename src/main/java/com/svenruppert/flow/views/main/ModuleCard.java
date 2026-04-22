package com.svenruppert.flow.views.main;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * Clickable card for the dashboard's module grid. A single
 * {@link Div} with a click listener that navigates to the module's
 * route; the hover lift and accent border are driven by the
 * {@code .dashboard-card} CSS in {@link MainView}.
 */
class ModuleCard extends Div {

  ModuleCard(String moduleNumber,
             String title,
             String description,
             String accentColour,
             String route) {
    addClassName("dashboard-card");
    // Only the dynamic accent colour remains as an inline property;
    // all other visual rules live in dashboard.css.
    getStyle().set("--dashboard-card-accent", accentColour);

    Span pill = new Span(moduleNumber);
    pill.addClassName("dashboard-card-pill");

    H3 heading = new H3(title);
    heading.addClassName("dashboard-card-heading");

    Paragraph body = new Paragraph(description);
    body.addClassName("dashboard-card-body");

    Icon chevron = VaadinIcon.CHEVRON_RIGHT.create();
    chevron.addClassName("dashboard-card-chevron");

    add(pill, heading, body, chevron);

    addClickListener(e -> UI.getCurrent().navigate(route));
    // Keyboard affordance: make the card focusable and navigate on Enter.
    getElement().setAttribute("tabindex", "0");
    getElement().setAttribute("role", "link");
    getElement().setAttribute("aria-label", title + " -- " + description);
    getElement().addEventListener("keydown", e -> UI.getCurrent().navigate(route))
        .setFilter("event.key === 'Enter'");
  }
}
