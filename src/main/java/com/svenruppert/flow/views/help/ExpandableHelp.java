package com.svenruppert.flow.views.help;

import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.details.DetailsVariant;

/**
 * Inline help panel attached to a user-facing control. Closed, it
 * reads as a small "What is this? -- &lt;title&gt;" link; opened, it
 * expands into an HTML block that explains purpose, valid values,
 * default and one practical hint.
 *
 * <p>The component is a thin wrapper around Vaadin's {@link Details}.
 * The FILLED + SMALL theme variants plus the {@code workshop-help}
 * class (styled by {@code workshop-help.css}, imported below) give
 * the panel a consistent, subdued appearance across all module views.
 */
@CssImport("./styles/workshop-help.css")
public class ExpandableHelp extends Details {

  public ExpandableHelp(HelpEntry entry) {
    super("");
    setSummaryText(getTranslation("help.summary", entry.title()));
    addClassName("workshop-help");
    addThemeVariants(DetailsVariant.FILLED, DetailsVariant.SMALL);
    setOpened(false);

    // The HTML body is authored by hand in ParameterDocs from a fixed
    // vocabulary (<p>, <ul>, <li>, <strong>, <code>); no user input
    // mixes in, so nothing to sanitise at the rendering seam.
    Html body = new Html(
        "<div class=\"workshop-help-body\">" + entry.html() + "</div>");
    add(body);
  }

  /** Convenience factory for inline use in view builders. */
  public static ExpandableHelp of(HelpEntry entry) {
    return new ExpandableHelp(entry);
  }
}
