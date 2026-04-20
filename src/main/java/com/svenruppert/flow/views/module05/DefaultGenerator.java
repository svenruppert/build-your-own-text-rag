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
 * <p>Consumes {@link StreamEvent}s:
 * <ul>
 *   <li>{@link StreamEvent.Token} events are forwarded to the
 *       {@code tokenSink} and accumulated into the answer text.</li>
 *   <li>{@link StreamEvent.Thinking} events are forwarded to the
 *       {@code thinkingSink} (if non-null) and accumulated into the
 *       answer's {@code thinking} field.</li>
 * </ul>
 * After the stream ends, {@link AttributionParser#parseReferences}
 * extracts the cited chunk indices and a small phrase heuristic flags
 * a refusal.
 *
 * <p>Grounding check is always {@link Optional#empty()} at this layer
 * -- {@link RagPipeline} attaches it afterwards when enabled.
 */
public final class DefaultGenerator implements Generator, HasLogger {

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
        return generate(query, hits, model, tokenSink, null);
    }

    @Override
    public GeneratedAnswer generate(String query,
                                    List<RetrievalHit> hits,
                                    String model,
                                    Consumer<String> tokenSink,
                                    Consumer<String> thinkingSink) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(hits, "hits");
        Objects.requireNonNull(model, "model");
        Consumer<String> tokens = (tokenSink == null) ? t -> { } : tokenSink;
        Consumer<String> thinking = (thinkingSink == null) ? t -> { } : thinkingSink;

        long start = System.nanoTime();
        String prompt = promptTemplate.buildPrompt(query, hits);
        StringBuilder answerBuffer = new StringBuilder();
        StringBuilder thinkingBuffer = new StringBuilder();

        try (Stream<StreamEvent> events = streamingApi.streamEvents(prompt, model)) {
            events.forEach(event -> {
                switch (event) {
                    case StreamEvent.Token t -> {
                        tokens.accept(t.text());
                        answerBuffer.append(t.text());
                    }
                    case StreamEvent.Thinking th -> {
                        thinking.accept(th.text());
                        thinkingBuffer.append(th.text());
                    }
                }
            });
        } catch (RuntimeException e) {
            logger().warn("streaming generation failed: {}", e.getMessage());
        }

        long latencyMillis = (System.nanoTime() - start) / 1_000_000L;
        String text = answerBuffer.toString();
        List<Integer> cited = AttributionParser.parseReferences(text, hits.size());
        boolean refusal = detectRefusal(text);

        return new GeneratedAnswer(text, cited, hits, refusal,
                Optional.empty(), latencyMillis, thinkingBuffer.toString());
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
