package com.svenruppert.flow.views.main;

import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

/**
 * Self-contained "big picture" zone for the dashboard.
 *
 * <p>Wraps {@link RagArchitectureDiagram} together with its caption and
 * all diagram-specific CSS. The outer {@code Div} carries the shared
 * {@code dashboard-zone} class so it sits correctly inside the
 * {@code MainView} layout without any knowledge of its surroundings.
 */
@CssImport("./styles/rag-big-picture.css")
final class RagBigPicture extends Div {

  RagBigPicture() {
    addClassName("dashboard-zone");
    setWidthFull();

    Span caption = new Span(getTranslation("main.diagram.caption"));
    caption.addClassName("dashboard-bigpicture-caption");

    add(new RagArchitectureDiagram(), caption);
  }

}
