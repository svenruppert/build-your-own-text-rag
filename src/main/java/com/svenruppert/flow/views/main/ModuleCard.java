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
    getStyle()
        .set("background", "var(--lumo-base-color)")
        .set("border", "1px solid var(--lumo-contrast-10pct)")
        .set("border-left", "3px solid " + accentColour)
        .set("border-radius", "12px")
        .set("padding", "var(--lumo-space-l)")
        .set("cursor", "pointer")
        .set("transition", "transform 0.15s, box-shadow 0.15s, border-left-color 0.15s")
        .set("display", "grid")
        .set("grid-template-columns", "1fr auto")
        .set("grid-template-rows", "auto auto auto")
        .set("column-gap", "var(--lumo-space-m)")
        .set("row-gap", "var(--lumo-space-xs)")
        .set("align-items", "start");

    Span pill = new Span(moduleNumber);
    pill.getStyle()
        .set("grid-column", "1")
        .set("grid-row", "1")
        .set("font-family", "ui-monospace, SFMono-Regular, monospace")
        .set("font-size", "0.72rem")
        .set("font-weight", "600")
        .set("letter-spacing", "0.04em")
        .set("padding", "0.15em 0.65em")
        .set("border-radius", "999px")
        .set("background", accentColour)
        .set("color", "#fff")
        .set("justify-self", "start");

    H3 heading = new H3(title);
    heading.getStyle()
        .set("grid-column", "1")
        .set("grid-row", "2")
        .set("margin", "0")
        .set("font-size", "1.2rem")
        .set("font-weight", "600");

    Paragraph body = new Paragraph(description);
    body.getStyle()
        .set("grid-column", "1")
        .set("grid-row", "3")
        .set("margin", "0")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("font-size", "0.95rem")
        .set("line-height", "1.5");

    Icon chevron = VaadinIcon.CHEVRON_RIGHT.create();
    chevron.getStyle()
        .set("grid-column", "2")
        .set("grid-row", "1 / span 3")
        .set("align-self", "center")
        .set("color", "var(--lumo-tertiary-text-color)")
        .set("--vaadin-icon-size", "1.1em");

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
