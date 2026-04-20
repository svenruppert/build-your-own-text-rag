package com.svenruppert.flow.views.module05;

import com.svenruppert.flow.views.module04.RetrievalHit;
import com.svenruppert.flow.views.module04.Retriever;
import com.svenruppert.flow.views.module04.testutil.TestChunks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagPipelineTest {

    private static final String MODEL = "stub-model";

    private static final List<RetrievalHit> HITS = List.of(
            new RetrievalHit(TestChunks.of(0, "alpha"), 0.9, "vector"),
            new RetrievalHit(TestChunks.of(1, "beta"), 0.8, "vector"));

    @Test
    @DisplayName("ask() calls retriever then generator with the retriever's hits")
    void ask_callsRetrieverThenGenerator() {
        AtomicReference<List<RetrievalHit>> seenByGenerator = new AtomicReference<>();
        AtomicBoolean retrieverCalled = new AtomicBoolean(false);

        Retriever retriever = (q, k) -> {
            retrieverCalled.set(true);
            return HITS;
        };
        Generator generator = (query, hits, model, sink) -> {
            seenByGenerator.set(hits);
            return answer("ok", false);
        };
        RagPipeline pipeline = new RagPipeline(retriever, generator, Optional.empty());

        GeneratedAnswer answer = pipeline.ask("question", 5, MODEL, null);
        assertTrue(retrieverCalled.get());
        assertSame(HITS, seenByGenerator.get());
        assertEquals("ok", answer.text());
    }

    @Test
    @DisplayName("no grounding checker -> result is returned verbatim")
    void ask_skipsGroundingCheckWhenCheckerAbsent() {
        RagPipeline pipeline = new RagPipeline(
                (q, k) -> HITS,
                (q, h, m, s) -> answer("answer", false),
                Optional.empty());

        GeneratedAnswer out = pipeline.ask("q", 5, MODEL, null);
        assertTrue(out.groundingCheck().isEmpty());
    }

    @Test
    @DisplayName("refusal short-circuits the grounding check even if a checker is wired")
    void ask_skipsGroundingCheckOnRefusal() {
        AtomicBoolean checkerCalled = new AtomicBoolean(false);
        GroundingChecker checker = (q, a, h, m) -> {
            checkerCalled.set(true);
            return GroundingResult.unknown();
        };
        RagPipeline pipeline = new RagPipeline(
                (q, k) -> HITS,
                (q, h, m, s) -> answer("I don't know.", true),
                Optional.of(checker));

        GeneratedAnswer out = pipeline.ask("q", 5, MODEL, null);
        assertFalse(checkerCalled.get(),
                "checker must not run on a refusal answer");
        assertTrue(out.groundingCheck().isEmpty());
    }

    @Test
    @DisplayName("grounding checker result is attached to the answer")
    void ask_attachesGroundingResultOnSuccessfulGeneration() {
        GroundingResult result = new GroundingResult(
                GroundingResult.Verdict.GROUNDED, "all claims backed.");
        GroundingChecker checker = (q, a, h, m) -> result;
        RagPipeline pipeline = new RagPipeline(
                (q, k) -> HITS,
                (q, h, m, s) -> answer("Paris is the capital of France.", false),
                Optional.of(checker));

        GeneratedAnswer out = pipeline.ask("q", 5, MODEL, null);
        assertTrue(out.groundingCheck().isPresent());
        assertSame(result, out.groundingCheck().get());
    }

    private static GeneratedAnswer answer(String text, boolean refusal) {
        return GeneratedAnswer.of(text, List.of(), HITS, refusal,
                Optional.empty(), 0L);
    }

    // Small smoke test to document that a null tokenSink is accepted.
    @Test
    @DisplayName("null tokenSink is accepted without error")
    void ask_acceptsNullTokenSink() {
        Consumer<String> nullSink = null;
        Generator generator = (q, h, m, s) -> {
            // Generators own the null-check; the pipeline just passes
            // whatever the caller gave us.
            assertTrue(s == null);
            return answer("ok", false);
        };
        new RagPipeline((q, k) -> HITS, generator, Optional.empty())
                .ask("q", 5, MODEL, nullSink);
    }
}
