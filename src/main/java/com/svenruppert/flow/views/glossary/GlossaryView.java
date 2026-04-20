package com.svenruppert.flow.views.glossary;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Browseable glossary backed by {@code /glossary/glossary.md}. Four
 * zones top to bottom: header, search (letter nav + text filter),
 * entries (Concepts and Tools sections), and footer.
 *
 * <p>The parsed Markdown is cached process-wide by
 * {@link GlossaryParser}; the view keeps its own per-instance list
 * of {@link GlossaryEntryCard} objects for live filtering.
 */
@Route(value = GlossaryView.PATH, layout = MainLayout.class)
@PageTitle("Glossary")
@StyleSheet("styles/glossary.css")
public class GlossaryView
    extends VerticalLayout
    implements HasLogger {

  public static final String PATH = "glossary";

  private final TextField searchField = new TextField();
  private final Span counterLabel = new Span();
  private final Div letterNav = new Div();
  private final Div conceptsBlock = new Div();
  private final Div toolsBlock = new Div();
  private final List<GlossaryEntryCard> cards = new ArrayList<>();

  public GlossaryView() {
    setSizeFull();
    setPadding(true);
    setSpacing(true);

    GlossaryParser.Parsed parsed = GlossaryParser.load();

    add(buildHeader(parsed.introHtml()));

    if (parsed.entries().isEmpty()) {
      Paragraph unavailable = new Paragraph("Glossary content unavailable.");
      unavailable.getStyle().set("color", "var(--lumo-secondary-text-color)");
      add(unavailable);
      add(buildFooter());
      return;
    }

    add(buildSearchZone());
    add(buildEntriesZone(parsed.entries()));
    add(buildFooter());

    updateCounter();
  }

  @Override
  protected void onAttach(AttachEvent event) {
    super.onAttach(event);
    // Install the page-wide anchor-click interceptor. The script
    // guards against double-binding via window.__glossaryAnchorsBound,
    // so repeated view attaches are safe.
    event.getUI().getPage().addJavaScript("js/glossary-anchors.js");
  }

  // ---------- zone 1: header ----------------------------------------

  private Component buildHeader(String introHtml) {
    VerticalLayout box = new VerticalLayout();
    box.setPadding(false);
    box.setSpacing(false);

    H1 heading = new H1("Glossary");
    heading.getStyle().set("margin", "0 0 var(--lumo-space-s) 0");
    box.add(heading);

    if (introHtml != null && !introHtml.isBlank()) {
      Div intro = new Div();
      intro.getStyle()
          .set("color", "var(--lumo-secondary-text-color)")
          .set("max-width", "640px")
          .set("line-height", "1.55");
      intro.add(new Html("<div>" + introHtml + "</div>"));
      box.add(intro);
    }
    return box;
  }

  // ---------- zone 2: search ----------------------------------------

  private Component buildSearchZone() {
    VerticalLayout box = new VerticalLayout();
    box.setPadding(false);
    box.setSpacing(true);

    letterNav.addClassName("glossary-letter-nav");
    box.add(letterNav);

    searchField.setPlaceholder("Filter terms...");
    searchField.setClearButtonVisible(true);
    searchField.setWidthFull();
    searchField.setValueChangeMode(ValueChangeMode.EAGER);
    searchField.addValueChangeListener(e -> applyFilter(e.getValue()));

    counterLabel.getStyle()
        .set("color", "var(--lumo-secondary-text-color)")
        .set("font-size", "0.9rem")
        .set("white-space", "nowrap");

    HorizontalLayout row = new HorizontalLayout(searchField, counterLabel);
    row.setWidthFull();
    row.setAlignItems(FlexComponent.Alignment.CENTER);
    row.setFlexGrow(1, searchField);
    box.add(row);
    return box;
  }

  // ---------- zone 3: entries ---------------------------------------

  private Component buildEntriesZone(List<GlossaryEntry> entries) {
    for (GlossaryEntry entry : entries) {
      GlossaryEntryCard card = new GlossaryEntryCard(entry);
      cards.add(card);
      if (entry.section() == GlossaryEntry.Section.CONCEPTS) {
        conceptsBlock.add(card);
      } else {
        toolsBlock.add(card);
      }
    }

    renderLetterNav();

    VerticalLayout box = new VerticalLayout();
    box.setPadding(false);
    box.setSpacing(false);

    H2 conceptsHeading = new H2("Concepts");
    styleSectionHeading(conceptsHeading);
    box.add(conceptsHeading, conceptsBlock);

    H2 toolsHeading = new H2("Tools");
    styleSectionHeading(toolsHeading);
    box.add(toolsHeading, toolsBlock);
    return box;
  }

  private void styleSectionHeading(H2 h) {
    h.getStyle()
        .set("font-size", "1.3rem")
        .set("font-weight", "600")
        .set("margin", "var(--lumo-space-l) 0 var(--lumo-space-s) 0")
        .set("color", "var(--lumo-primary-text-color)");
  }

  /**
   * Renders the A-Z letter strip. Each enabled letter is an anchor
   * whose click scrolls the first matching entry into view through
   * the same JS hook as intra-body cross-references.
   */
  private void renderLetterNav() {
    letterNav.removeAll();
    for (char c = 'A'; c <= 'Z'; c++) {
      char letter = c;
      Optional<GlossaryEntryCard> first = firstCardStartingWith(letter);
      if (first.isPresent()) {
        Html link = new Html(
            "<a href=\"#" + first.get().getAnchor() + "\">" + letter + "</a>");
        letterNav.add(link);
      } else {
        Html disabled = new Html(
            "<a class=\"disabled\">" + letter + "</a>");
        letterNav.add(disabled);
      }
    }
  }

  private Optional<GlossaryEntryCard> firstCardStartingWith(char letter) {
    String prefix = String.valueOf(Character.toUpperCase(letter));
    return cards.stream()
        .filter(card -> card.entryTitle().toUpperCase(Locale.ROOT).startsWith(prefix))
        .findFirst();
  }

  // ---------- filtering ---------------------------------------------

  private void applyFilter(String value) {
    for (GlossaryEntryCard card : cards) {
      card.setVisible(card.matches(value));
    }
    updateCounter();
  }

  private void updateCounter() {
    long shown = cards.stream().filter(Component::isVisible).count();
    counterLabel.setText(shown + " of " + cards.size() + " terms shown");
  }

  // ---------- zone 4: footer ----------------------------------------

  private Component buildFooter() {
    Paragraph footer = new Paragraph(
        "Thirty terms. Workshop: Building Secure, Self-Hosted RAG Systems in Java.");
    footer.getStyle()
        .set("color", "var(--lumo-tertiary-text-color)")
        .set("font-size", "0.85rem")
        .set("margin-top", "var(--lumo-space-l)");
    return footer;
  }
}
