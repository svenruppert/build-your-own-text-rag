package com.svenruppert.flow.views.help;

import com.svenruppert.flow.views.module05.RagPipeline;

/**
 * Maps a {@link RagPipeline.Stage} transition to a progress-bar
 * fraction and an i18n label suffix. Centralises the two nearly
 * identical {@code applyStage} switches that lived in Module05View
 * and Module06View.
 *
 * <p>The fractions are <em>coarse on purpose</em>: the phases are
 * few, their durations wildly different, and the intent is to tell
 * participants what the pipeline is <em>doing</em> right now, not to
 * estimate true time-to-completion.
 *
 * <p>The {@code labelSuffix} is returned without a module prefix so
 * each view can build its own key (e.g. {@code "m05.stage." + suffix}).
 *
 * <p>When {@code groundingEnabled} is false, {@code GENERATION_FINISHED}
 * is the last stage before {@link RagPipeline.Stage#DONE} and therefore
 * maps to fraction 1.0 with label suffix {@code "done"}.
 */
public final class StageProgress {

  /** A mapping from {@link RagPipeline.Stage} to UI feedback. */
  public record Phase(double fraction, String labelSuffix) { }

  private StageProgress() { }

  /**
   * Returns the {@link Phase} for {@code stage}. The only branch on
   * {@code groundingEnabled} is {@code GENERATION_FINISHED}: without
   * grounding that stage ends the run; with grounding it advances to
   * 0.80 so the two grounding stages fit between it and 1.0.
   */
  public static Phase phase(RagPipeline.Stage stage, boolean groundingEnabled) {
    return switch (stage) {
      case RETRIEVAL_STARTED  -> new Phase(0.05, "retrieval.started");
      case RETRIEVAL_FINISHED -> new Phase(0.15, "retrieval.finished");
      case GENERATION_STARTED -> new Phase(0.20, "generation.started");
      case GENERATION_FINISHED -> groundingEnabled
          ? new Phase(0.80, "generation.finished")
          : new Phase(1.0, "done");
      case GROUNDING_STARTED  -> new Phase(0.85, "grounding.started");
      case GROUNDING_FINISHED -> new Phase(1.0, "grounding.finished");
      case DONE               -> new Phase(1.0, "done");
    };
  }
}
