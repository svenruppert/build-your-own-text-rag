package com.svenruppert.flow.views.help;

import java.util.Objects;

/**
 * A single parameter help entry: an i18n key for the summary title
 * and the base name of the HTML fragment file loaded from
 * {@code src/main/resources/help/{locale}/{htmlFile}.html} at runtime.
 *
 * <p>Kept intentionally dumb -- no loading logic, no escaping. The
 * HTML files are authored by hand from a fixed vocabulary
 * and never mixed with user input.
 *
 * @param titleKey i18n key for the panel's summary title
 * @param htmlFile base file name without extension, e.g. {@code "m1_model"}
 */
public record HelpEntry(String titleKey, String htmlFile) {

  /**
   * Compact canonical constructor -- validates that neither field is null.
   *
   * @param titleKey i18n key for the panel's summary title
   * @param htmlFile base file name without extension
   */
  public HelpEntry {
    Objects.requireNonNull(titleKey, "titleKey");
    Objects.requireNonNull(htmlFile, "htmlFile");
  }
}
