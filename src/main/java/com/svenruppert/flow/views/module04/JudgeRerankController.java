package com.svenruppert.flow.views.module04;

import com.svenruppert.flow.views.module01.LlmClient;
import com.svenruppert.flow.views.module03.Chunk;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the "judge thinking" side panel and drives the
 * {@link LlmJudgeReranker} rerank pass from Module 4's Retrieval Lab.
 *
 * <p>The controller exposes the collapsible Details panel as a
 * {@link Component} so the view can park it anywhere in its layout.
 * During a rerank call it accumulates per-candidate {@code <think>...}
 * blocks into the panel incrementally; the progress observer is passed
 * in by the caller because the progress bar + status label it drives
 * belong to the view's overall search UI.
 *
 * <p>Intended use:
 * <pre>{@code
 * JudgeRerankController judge = new JudgeRerankController(
 *     getTranslation("m04.judge.thinking.title"),
 *     offset -> getTranslation("m04.judge.thinking.chunk", offset),
 *     shown  -> getTranslation("m04.judge.thinking.summary", shown));
 * add(judge.panel());
 *
 * // on each search:
 * judge.reset();
 * List<RetrievalHit> reranked = judge.rerank(ui, llmClient, model, query,
 *     firstStage, topK, progressObserver);
 * }</pre>
 */
public final class JudgeRerankController {

  /** Formats the per-entry heading shown inside the thinking panel. */
  @FunctionalInterface
  public interface EntryHeadingFormatter {
    String format(long startOffset);
  }

  /** Formats the panel's summary text ("N reasoned candidates"). */
  @FunctionalInterface
  public interface SummaryFormatter {
    String format(int shown);
  }

  private final EntryHeadingFormatter entryHeading;
  private final SummaryFormatter summary;
  private final Map<Chunk, String> judgeThinking = new LinkedHashMap<>();
  private final Div body = new Div();
  private final Details panel;

  public JudgeRerankController(String panelTitle,
                               EntryHeadingFormatter entryHeading,
                               SummaryFormatter summary) {
    this.entryHeading = entryHeading;
    this.summary = summary;
    body.addClassName("judge-thinking-body");
    panel = new Details(panelTitle, body);
    panel.addClassName("judge-thinking-panel");
    panel.setOpened(false);
    panel.setVisible(false);
  }

  /** The Details component; attach once to your layout. */
  public Component panel() {
    return panel;
  }

  /** Clears captured thinking and hides the panel for a fresh search. */
  public void reset() {
    judgeThinking.clear();
    body.removeAll();
    panel.setVisible(false);
  }

  /**
   * Runs the LLM-as-judge rerank. The thinking observer is owned
   * internally and pushes updates through {@code ui.access}; the
   * caller supplies the progress observer so it can drive its own
   * progress bar and status label.
   */
  public List<RetrievalHit> rerank(UI ui,
                                   LlmClient llmClient,
                                   String judgeModel,
                                   String query,
                                   List<RetrievalHit> firstStageHits,
                                   int topK,
                                   LlmJudgeReranker.ProgressObserver progressObserver) {
    LlmJudgeReranker reranker = new LlmJudgeReranker(
        llmClient, judgeModel,
        (candidate, thinking) -> ui.access(() -> {
          judgeThinking.put(candidate.chunk(), thinking);
          rebuildPanel();
        }),
        progressObserver);
    return reranker.rerank(query, firstStageHits, topK);
  }

  /**
   * Rebuilds the panel body from the captured thinking map. Runs on
   * the UI thread (the thinking observer already hops via
   * {@code ui.access}), so direct DOM mutation is safe.
   */
  private void rebuildPanel() {
    body.removeAll();
    if (judgeThinking.isEmpty()) {
      panel.setVisible(false);
      return;
    }
    int shown = 0;
    for (Map.Entry<Chunk, String> entry : judgeThinking.entrySet()) {
      if (entry.getValue() == null || entry.getValue().isEmpty()) continue;
      Div box = new Div();
      box.addClassName("judge-thinking-entry");
      Span heading = new Span(entryHeading.format(entry.getKey().startOffset()));
      heading.addClassName("heading");
      box.add(heading, new Span(entry.getValue()));
      body.add(box);
      shown++;
    }
    if (shown == 0) {
      panel.setVisible(false);
      return;
    }
    panel.setSummaryText(summary.format(shown));
    panel.setVisible(true);
    panel.setOpened(true);
  }
}
