package com.svenruppert.flow.views.help;

import java.util.Objects;

/**
 * A single parameter help entry: a short title for the summary line
 * and an HTML body rendered inside the expandable panel.
 *
 * <p>Kept intentionally dumb -- no escaping, no validation beyond
 * null-checks. The HTML is authored by hand in {@link ParameterDocs}
 * from a fixed vocabulary ({@code <p>}, {@code <ul>}, {@code <li>},
 * {@code <strong>}, {@code <code>}) and never mixed with user input,
 * so there is nothing to sanitise.
 */
public record HelpEntry(String title, String html) {
  public HelpEntry {
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(html, "html");
  }
}
