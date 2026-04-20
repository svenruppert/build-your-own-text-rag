package com.svenruppert.flow.views.glossary;

import java.util.List;
import java.util.Objects;

/** One parsed glossary entry: anchor, title, body HTML, cross-references. */
public record GlossaryEntry(
    String anchor,
    String title,
    String bodyHtml,
    List<String> crossReferenceAnchors,
    Section section) {

  public enum Section { CONCEPTS, TOOLS }

  public GlossaryEntry {
    Objects.requireNonNull(anchor);
    Objects.requireNonNull(title);
    Objects.requireNonNull(bodyHtml);
    Objects.requireNonNull(section);
    crossReferenceAnchors = List.copyOf(crossReferenceAnchors);
  }
}
