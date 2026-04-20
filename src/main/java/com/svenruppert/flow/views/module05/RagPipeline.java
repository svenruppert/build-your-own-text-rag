package com.svenruppert.flow.views.module05;

import com.svenruppert.flow.views.module04.RetrievalHit;
import com.svenruppert.flow.views.module04.Retriever;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * End-to-end RAG orchestrator: retrieve, generate, optionally check.
 *
 * <p>Composes {@link Retriever}, {@link Generator} and an optional
 * {@link GroundingChecker} into one {@link #ask} call. The grounding
 * check is skipped in two explicit cases:
 * <ul>
 *   <li>no {@link GroundingChecker} was injected,</li>
 *   <li>the generator detected a refusal -- checking "I don't know."
 *       against a set of chunks would answer itself.</li>
 * </ul>
 */
public final class RagPipeline {

    /**
     * Coarse pipeline phases reported through {@link StageListener}.
     * Each phase fires twice -- STARTED as it begins, FINISHED as it
     * hands off to the next -- so a UI can drive a determinate
     * progress bar without having to guess at step timings.
     */
    public enum Stage {
        RETRIEVAL_STARTED, RETRIEVAL_FINISHED,
        GENERATION_STARTED, GENERATION_FINISHED,
        GROUNDING_STARTED, GROUNDING_FINISHED,
        DONE
    }

    /** Phase transitions reported while {@link #ask} runs. */
    @FunctionalInterface
    public interface StageListener {
        void onStage(Stage stage);
    }

    private static final StageListener NOOP_STAGE = stage -> { };

    private final Retriever retriever;
    private final Generator generator;
    private final Optional<GroundingChecker> groundingChecker;

    public RagPipeline(Retriever retriever,
                       Generator generator,
                       Optional<GroundingChecker> groundingChecker) {
        this.retriever = Objects.requireNonNull(retriever, "retriever");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.groundingChecker = Objects.requireNonNull(groundingChecker, "groundingChecker");
    }

    /**
     * Retrieves the top {@code retrievalK} hits for {@code query},
     * hands them to the generator, streams tokens through
     * {@code tokenSink}, and optionally runs the grounding check.
     */
    public GeneratedAnswer ask(String query, int retrievalK, String model,
                               Consumer<String> tokenSink) {
        return ask(query, retrievalK, model, tokenSink, null, null);
    }

    /**
     * Thinking-aware variant: a reasoning model's thinking tokens are
     * forwarded to {@code thinkingSink} while the answer tokens land
     * on {@code tokenSink}. Everything else is identical to the
     * 4-argument overload.
     */
    public GeneratedAnswer ask(String query, int retrievalK, String model,
                               Consumer<String> tokenSink,
                               Consumer<String> thinkingSink) {
        return ask(query, retrievalK, model, tokenSink, thinkingSink, null);
    }

    /**
     * Full-fat overload: same as the thinking-aware {@link #ask} plus a
     * {@link StageListener} that receives phase transitions. A
     * {@code null} listener falls back to a no-op.
     */
    public GeneratedAnswer ask(String query, int retrievalK, String model,
                               Consumer<String> tokenSink,
                               Consumer<String> thinkingSink,
                               StageListener stageListener) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(model, "model");
        if (retrievalK <= 0) {
            throw new IllegalArgumentException("retrievalK must be > 0, got " + retrievalK);
        }
        StageListener stages = (stageListener == null) ? NOOP_STAGE : stageListener;

        stages.onStage(Stage.RETRIEVAL_STARTED);
        List<RetrievalHit> hits = retriever.retrieve(query, retrievalK);
        stages.onStage(Stage.RETRIEVAL_FINISHED);

        stages.onStage(Stage.GENERATION_STARTED);
        GeneratedAnswer answer = generator.generate(
                query, hits, model, tokenSink, thinkingSink);
        stages.onStage(Stage.GENERATION_FINISHED);

        boolean skipCheck = groundingChecker.isEmpty()
                || answer.text().isBlank()
                || answer.refusalDetected();
        if (skipCheck) {
            stages.onStage(Stage.DONE);
            return answer;
        }

        stages.onStage(Stage.GROUNDING_STARTED);
        GroundingResult grounding = groundingChecker.get()
                .check(query, answer.text(), hits, model);
        stages.onStage(Stage.GROUNDING_FINISHED);
        stages.onStage(Stage.DONE);
        return answer.withGrounding(grounding);
    }
}
