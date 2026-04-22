package com.svenruppert.flow.views.help;

import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.details.DetailsVariant;

/**
 * Inline help panel attached to a user-facing control. Closed, it
 * reads as a small "What is this? -- title" link; opened, it
 * expands into an HTML block that explains purpose, valid values,
 * default and one practical hint.
 *
 * <p>The summary label is resolved via {@code getTranslation} so it
 * honours the active UI locale. The HTML body is loaded from a
 * locale-specific resource file via {@link HelpLoader}; English is
 * the fallback when no translation file is present.
 *
 * <p>The component is a thin wrapper around Vaadin's {@link Details}.
 * The FILLED + SMALL theme variants plus the {@code workshop-help}
 * class (styled by {@code workshop-help.css}, imported below) give
 * the panel a consistent, subdued appearance across all module views.
 */
@CssImport("./styles/workshop-help.css")
public class ExpandableHelp extends Details {

  /**
   * Creates a help panel for the given entry, loading the HTML body
   * from the locale-specific resource file.
   *
   * @param entry the help entry providing the title key and file name
   */
  public ExpandableHelp(final HelpEntry entry) {
    super("");
    setSummaryText(
        getTranslation("help.summary", getTranslation(entry.titleKey())));
    addClassName("workshop-help");
    addThemeVariants(DetailsVariant.FILLED, DetailsVariant.SMALL);
    setOpened(false);

    String html = HelpLoader.loadHtml(entry.htmlFile(), getLocale());
    Html body = new Html(
        "<div class=\"workshop-help-body\">" + html + "</div>");
    add(body);
  }

  /**
   * Convenience factory for inline use in view builders.
   *
   * @param entry the help entry to render
   * @return a new {@link ExpandableHelp} instance for the entry
   */
  public static ExpandableHelp of(final HelpEntry entry) {
    return new ExpandableHelp(entry);
  }
}
