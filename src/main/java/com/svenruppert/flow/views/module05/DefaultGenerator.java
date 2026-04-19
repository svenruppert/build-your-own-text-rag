package com.svenruppert.flow.views.module05;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.views.module04.RetrievalHit;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Streaming {@link Generator} on top of a {@link StreamingLlmApi} and
 * a {@link PromptTemplate}.
 *
 * <p>Forwards every token from the stream to the caller-provided
 * {@code tokenSink} in order and accumulates the same tokens into a
 * {@link StringBuilder}. When the stream ends,
 * {@link AttributionParser#parseReferences} extracts the cited chunk
 * indices and a small phrase heuristic flags a refusal.
 *
 * <p>Grounding check is always {@link Optional#empty()} at this layer
 * -- {@link RagPipeline} attaches it afterwards when enabled.
 */
public final class DefaultGenerator implements Generator, HasLogger {

    /**
     * Lower-cased refusal phrases. Matched against the first 200
     * characters of the answer -- the refusal, if present, always
     * appears at the start of the reply.
     */
    private static final List<String> REFUSAL_PHRASES = List.of(
            "i don't know",
            "i do not know",
            "cannot answer",
            "insufficient information");

    private static final int REFUSAL_SCAN_LIMIT = 200;

    private final StreamingLlmApi streamingApi;
    private final PromptTemplate promptTemplate;

    public DefaultGenerator(StreamingLlmApi streamingApi, PromptTemplate promptTemplate) {
        this.streamingApi = Objects.requireNonNull(streamingApi, "streamingApi");
        this.promptTemplate = Objects.requireNonNull(promptTemplate, "promptTemplate");
    }

    @Override
    public GeneratedAnswer generate(String query,
                                    List<RetrievalHit> hits,
                                    String model,
                                    Consumer<String> tokenSink) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(hits, "hits");
        Objects.requireNonNull(model, "model");
        Consumer<String> sink = (tokenSink == null) ? t -> { } : tokenSink;

        long start = System.nanoTime();
        String prompt = promptTemplate.buildPrompt(query, hits);
        StringBuilder accumulator = new StringBuilder();

        try (Stream<String> tokens = streamingApi.streamGenerate(prompt, model)) {
            tokens.forEach(token -> {
                sink.accept(token);
                accumulator.append(token);
            });
        } catch (RuntimeException e) {
            logger().warn("streaming generation failed: {}", e.getMessage());
        }

        long latencyMillis = (System.nanoTime() - start) / 1_000_000L;
        String text = accumulator.toString();
        List<Integer> cited = AttributionParser.parseReferences(text, hits.size());
        boolean refusal = detectRefusal(text);

        return new GeneratedAnswer(text, cited, hits, refusal, Optional.empty(), latencyMillis);
    }

    /**
     * True iff the first {@value #REFUSAL_SCAN_LIMIT} characters of
     * the answer (lower-cased) contain one of the refusal phrases. A
     * heuristic, not a classifier: good enough to gate the grounding
     * check and to flag the answer in the UI.
     */
    private boolean detectRefusal(String text) {
        if (text.isEmpty()) return false;
        String prefix = text.substring(0, Math.min(REFUSAL_SCAN_LIMIT, text.length()))
                .toLowerCase(Locale.ROOT);
        for (String phrase : REFUSAL_PHRASES) {
            if (prefix.contains(phrase)) return true;
        }
        return false;
    }
}
