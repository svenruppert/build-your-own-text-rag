package com.svenruppert.flow.views.glossary;

import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;

import java.util.Locale;

/**
 * Visual card for a single {@link GlossaryEntry}. Carries the anchor
 * {@code id} so browser-native {@code #fragment} URLs scroll to the
 * right card; exposes {@link #matches(String)} for the view's live
 * search filter.
 */
class GlossaryEntryCard extends Div {

  private final GlossaryEntry entry;
  private final String searchHaystack;

  GlossaryEntryCard(GlossaryEntry entry) {
    this.entry = entry;
    this.searchHaystack =
        (entry.title() + " " + stripHtml(entry.bodyHtml())).toLowerCase(Locale.ROOT);

    addClassName("glossary-entry");
    setId(entry.anchor());

    H3 title = new H3(entry.title());
    title.addClassName("glossary-entry-title");

    Div body = new Div();
    body.addClassName("glossary-entry-body");
    body.add(new Html("<div>" + entry.bodyHtml() + "</div>"));

    add(title, body);
  }

  String getAnchor() {
    return entry.anchor();
  }

  String entryTitle() {
    return entry.title();
  }

  GlossaryEntry.Section getSection() {
    return entry.section();
  }

  boolean matches(String searchString) {
    if (searchString == null || searchString.isBlank()) return true;
    return searchHaystack.contains(searchString.toLowerCase(Locale.ROOT));
  }

  private static String stripHtml(String html) {
    return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ");
  }
}
