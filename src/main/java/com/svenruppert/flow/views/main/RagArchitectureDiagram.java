package com.svenruppert.flow.views.main;

import com.svenruppert.flow.views.module02.Module02View;
import com.svenruppert.flow.views.module03.Module03View;
import com.svenruppert.flow.views.module04.Module04View;
import com.svenruppert.flow.views.module05.Module05View;
import com.svenruppert.flow.views.module06.Module06View;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

/**
 * Vaadin-native rendering of the workshop's RAG overview diagram.
 *
 * <p>The component mirrors the former SVG composition with regular
 * Flow components: coloured bands, positioned step cards, a central
 * vector-store panel and arrow connectors built from styled {@link Div}
 * elements. This keeps the dashboard purely component-based and makes
 * the diagram themeable and extensible from Java.</p>
 */
final class RagArchitectureDiagram extends Div {

  RagArchitectureDiagram() {
    addClassName("rag-architecture");
    getElement().setAttribute("role", "img");
    getElement().setAttribute("aria-label", getTranslation("main.diagram.aria"));

    add(
        band("rag-architecture__band--top"),
        band("rag-architecture__band--bottom"),
        zoneBar("rag-architecture__zone-bar--top"),
        zoneBar("rag-architecture__zone-bar--bottom"),
        zoneLabel(
            getTranslation("main.diagram.indexing.title"),
            getTranslation("main.diagram.indexing.subtitle"),
            "rag-architecture__zone-label--top",
            "rag-architecture__zone-title--top"),
        zoneLabel(
            getTranslation("main.diagram.query.title"),
            getTranslation("main.diagram.query.subtitle"),
            "rag-architecture__zone-label--bottom",
            "rag-architecture__zone-title--bottom"),
        jvmLabel(),

        // Indexing row: 3 nodes × 14% width, arrows of 3% each.
        // Embedder is centered at 50 % to align with the arrow-down
        // to the Vector Store (left=43 % + 7 % = 50 %).
        node(getTranslation("main.diagram.node.documents"),
            getTranslation("main.diagram.node.documents.sub"),
            "rag-architecture__node--indexing",
            "7%", "23.333%", "14%", "8.889%",
            Module03View.PATH),
        node(getTranslation("main.diagram.node.chunker"),
            getTranslation("main.diagram.node.chunker.sub"),
            "rag-architecture__node--indexing",
            "25%", "23.333%", "14%", "8.889%",
            Module03View.PATH),
        node(getTranslation("main.diagram.node.embedder"),
            getTranslation("main.diagram.node.embedder.sub"),
            "rag-architecture__node--indexing",
            "43%", "23.333%", "14%", "8.889%",
            Module02View.PATH),

        arrowRight("rag-architecture__arrow-right--indexing",
            "21.25%", "27.778%", "3.5%"),
        arrowRight("rag-architecture__arrow-right--indexing",
            "39.25%", "27.778%", "3.5%"),
        arrowDown("rag-architecture__arrow-down--indexing",
            "49.94%", "33.111%", "8.222%"),

        vectorStore(),

        // Dashed arrows aligned with Retrieve node centre (42 %).
        dashedArrow("rag-architecture__arrow-dashed--up",
            "41.5%", "59.111%", "18.889%"),
        dashedArrow("rag-architecture__arrow-dashed--down",
            "42.8%", "59.111%", "18.889%"),
        flowLabel(getTranslation("main.diagram.flow.query"), "rag-architecture__flow-label--left"),
        flowLabel(getTranslation("main.diagram.flow.chunks"), "rag-architecture__flow-label--right"),

        // Query row: 6 nodes × 14% width, arrows of 1.5% each.
        node(getTranslation("main.diagram.node.query"),
            getTranslation("main.diagram.node.query.sub"),
            "rag-architecture__node--query",
            "2%", "78.889%", "14%", "8.889%",
            Module05View.PATH),
        node(getTranslation("main.diagram.node.embedder"),
            getTranslation("main.diagram.node.embedder.sub"),
            "rag-architecture__node--query",
            "18.5%", "78.889%", "14%", "8.889%",
            Module02View.PATH),
        node(getTranslation("main.diagram.node.retrieve"),
            getTranslation("main.diagram.node.retrieve.sub"),
            "rag-architecture__node--query",
            "35%", "78.889%", "14%", "8.889%",
            Module04View.PATH),
        node(getTranslation("main.diagram.node.prompt"),
            getTranslation("main.diagram.node.prompt.sub"),
            "rag-architecture__node--query",
            "51.5%", "78.889%", "14%", "8.889%",
            Module05View.PATH),
        node(getTranslation("main.diagram.node.llm"),
            getTranslation("main.diagram.node.llm.sub"),
            "rag-architecture__node--query",
            "68%", "78.889%", "14%", "8.889%",
            Module05View.PATH),
        node(getTranslation("main.diagram.node.ui"),
            getTranslation("main.diagram.node.ui.sub"),
            "rag-architecture__node--query",
            "84.5%", "78.889%", "14%", "8.889%",
            Module06View.PATH),

        arrowRight("rag-architecture__arrow-right--query",
            "16.25%", "83.333%", "2%"),
        arrowRight("rag-architecture__arrow-right--query",
            "32.75%", "83.333%", "2%"),
        arrowRight("rag-architecture__arrow-right--query",
            "49.25%", "83.333%", "2%"),
        arrowRight("rag-architecture__arrow-right--query",
            "65.75%", "83.333%", "2%"),
        arrowRight("rag-architecture__arrow-right--query",
            "82.25%", "83.333%", "2%")
    );
  }

  private Div band(String modifierClass) {
    Div band = new Div();
    band.addClassNames("rag-architecture__band", modifierClass);
    return band;
  }

  private Div zoneBar(String modifierClass) {
    Div bar = new Div();
    bar.addClassNames("rag-architecture__zone-bar", modifierClass);
    return bar;
  }

  private Div zoneLabel(String title,
                        String subtitle,
                        String placementClass,
                        String titleToneClass) {
    Div wrapper = new Div();
    wrapper.addClassNames("rag-architecture__zone-label", placementClass);

    Span titleSpan = new Span(title);
    titleSpan.addClassNames("rag-architecture__zone-title", titleToneClass);

    Span subtitleSpan = new Span(subtitle);
    subtitleSpan.addClassName("rag-architecture__zone-subtitle");

    wrapper.add(titleSpan, subtitleSpan);
    return wrapper;
  }

  private Div jvmLabel() {
    Div wrapper = new Div();
    wrapper.addClassName("rag-architecture__jvm");

    Div copy = new Div();
    copy.addClassName("rag-architecture__jvm-copy");

    Span title = new Span(getTranslation("main.diagram.jvm.title"));
    title.addClassName("rag-architecture__jvm-title");

    Span subtitle = new Span(getTranslation("main.diagram.jvm.subtitle"));
    subtitle.addClassName("rag-architecture__jvm-subtitle");

    Div rail = new Div();
    rail.addClassName("rag-architecture__jvm-rail");

    copy.add(title, subtitle);
    wrapper.add(copy, rail);
    return wrapper;
  }

  private Div node(String title,
                   String subtitle,
                   String toneClass,
                   String left,
                   String top,
                   String width,
                   String height,
                   String route) {
    Div node = new Div();
    node.addClassNames("rag-architecture__node", toneClass);
    place(node, left, top, width, height);

    if (route != null) {
      node.addClassName("rag-architecture__node--clickable");
      node.getElement().setAttribute("role", "button");
      node.getElement().setAttribute("tabindex", "0");
      node.addClickListener(e -> UI.getCurrent().navigate(route));
      node.getElement()
          .addEventListener("keydown", e -> UI.getCurrent().navigate(route))
          .setFilter("event.key === 'Enter'");
    }

    Span titleSpan = new Span(title);
    titleSpan.addClassName("rag-architecture__node-title");

    Span subtitleSpan = new Span(subtitle);
    subtitleSpan.addClassName("rag-architecture__node-subtitle");

    node.add(titleSpan, subtitleSpan);
    return node;
  }

  private Div vectorStore() {
    Div store = new Div();
    store.addClassNames("rag-architecture__store", "rag-architecture__store--clickable");
    store.getElement().setAttribute("role", "button");
    store.getElement().setAttribute("tabindex", "0");
    store.addClickListener(e -> UI.getCurrent().navigate(Module02View.PATH));
    store.getElement()
        .addEventListener("keydown", e -> UI.getCurrent().navigate(Module02View.PATH))
        .setFilter("event.key === 'Enter'");

    Span kicker = new Span(getTranslation("main.diagram.store.kicker"));
    kicker.addClassName("rag-architecture__store-kicker");

    Span title = new Span(getTranslation("main.diagram.store.title"));
    title.addClassName("rag-architecture__store-title");

    Span subtitle = new Span(getTranslation("main.diagram.store.subtitle"));
    subtitle.addClassName("rag-architecture__store-subtitle");

    store.add(kicker, title, subtitle);
    return store;
  }

  private Div arrowRight(String toneClass, String left, String top, String width) {
    Div arrow = new Div();
    arrow.addClassNames("rag-architecture__arrow-right", toneClass);
    arrow.getStyle()
        .set("left", left)
        .set("top", top)
        .set("width", width);
    return arrow;
  }

  private Div arrowDown(String toneClass, String left, String top, String height) {
    Div arrow = new Div();
    arrow.addClassNames("rag-architecture__arrow-down", toneClass);
    arrow.getStyle()
        .set("left", left)
        .set("top", top)
        .set("height", height);
    return arrow;
  }

  private Div dashedArrow(String directionClass, String left, String top, String height) {
    Div arrow = new Div();
    arrow.addClassNames("rag-architecture__arrow-dashed", directionClass);
    arrow.getStyle()
        .set("left", left)
        .set("top", top)
        .set("height", height);
    return arrow;
  }

  private Div flowLabel(String text, String placementClass) {
    Div label = new Div();
    label.addClassNames("rag-architecture__flow-label", placementClass);
    label.setText(text);
    return label;
  }

  private void place(Div component,
                     String left,
                     String top,
                     String width,
                     String height) {
    component.getStyle()
        .set("left", left)
        .set("top", top)
        .set("width", width)
        .set("height", height);
  }
}
