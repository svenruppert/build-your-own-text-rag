package com.svenruppert.flow.views.help;

import com.svenruppert.flow.views.module03.Chunk;
import com.svenruppert.flow.views.module04.RetrievalHit;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Renders the "Sources" side panel used by the RAG answer views.
 */
public final class RetrievalSourcesPanel extends VerticalLayout {

  public RetrievalSourcesPanel() {
    setPadding(false);
    setSpacing(false);
    getStyle().set("overflow-y", "auto");
  }

  public void renderWithCitations(List<RetrievalHit> hits,
                                  Set<Integer> citedIndices,
                                  Function<RetrievalHit, String> idProvider) {
    render(hits, citedIndices, idProvider, 120, true, false);
  }

  public void renderProductSources(List<RetrievalHit> hits) {
    render(hits, Set.of(), hit -> "", 140, false, true);
  }

  private void render(List<RetrievalHit> hits,
                      Set<Integer> citedIndices,
                      Function<RetrievalHit, String> idProvider,
                      int previewMax,
                      boolean showHeadingPath,
                      boolean markAllCited) {
    removeAll();
    int number = 1;
    for (RetrievalHit hit : hits) {
      int zeroBased = number - 1;
      boolean cited = markAllCited || citedIndices.contains(zeroBased);

      Div item = new Div();
      item.addClassName("source-item");
      if (cited) item.addClassName("cited");

      Span label = new Span(labelText(number, hit, idProvider));
      label.addClassName("source-label");

      Div preview = new Div();
      preview.addClassName("source-preview");
      preview.setText(preview(hit.chunk().text(), previewMax));

      item.add(label, preview);
      if (showHeadingPath) {
        addHeadingPath(item, hit);
      }
      add(item);
      number++;
    }
  }

  private static String labelText(int number,
                                  RetrievalHit hit,
                                  Function<RetrievalHit, String> idProvider) {
    String id = idProvider.apply(hit);
    return id == null || id.isBlank()
        ? "[Chunk " + number + "]"
        : "[Chunk " + number + "]   " + id;
  }

  private static void addHeadingPath(Div item, RetrievalHit hit) {
    Object headingPath = hit.chunk().metadata().get(Chunk.HEADING_PATH);
    if (headingPath == null || headingPath.toString().isEmpty()) return;
    Div path = new Div();
    path.addClassName("source-path");
    path.setText(headingPath.toString());
    item.add(path);
  }

  private static String preview(String text, int max) {
    String flat = text.replace('\n', ' ').replace('\r', ' ').trim();
    return flat.length() <= max ? flat : flat.substring(0, max) + "...";
  }
}
