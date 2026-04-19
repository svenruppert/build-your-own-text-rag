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
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(model, "model");
        if (retrievalK <= 0) {
            throw new IllegalArgumentException("retrievalK must be > 0, got " + retrievalK);
        }

        List<RetrievalHit> hits = retriever.retrieve(query, retrievalK);
        GeneratedAnswer answer = generator.generate(query, hits, model, tokenSink);

        boolean skipCheck = groundingChecker.isEmpty()
                || answer.text().isBlank()
                || answer.refusalDetected();
        if (skipCheck) return answer;

        GroundingResult grounding = groundingChecker.get()
                .check(query, answer.text(), hits, model);
        return answer.withGrounding(grounding);
    }
}
