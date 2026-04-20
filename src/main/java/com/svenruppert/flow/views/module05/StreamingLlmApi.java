package com.svenruppert.flow.views.module05;

import java.util.stream.Stream;

/**
 * HTTP-backed streaming completion.
 *
 * <p>Primary method: {@link #streamEvents}, which yields a stream of
 * {@link StreamEvent}s -- {@link StreamEvent.Token} for user-facing
 * answer tokens and {@link StreamEvent.Thinking} for the internal
 * reasoning tokens that thinking models (deepseek-r1, qwen3-*thinking*,
 * ...) emit in a separate channel.
 *
 * <p>{@link #streamGenerate} is a convenience that keeps only the
 * Token events' text -- handy for tests and for callers that do not
 * care about thinking.
 *
 * <p>The returned stream is backed by a live HTTP connection.
 * <strong>The caller owns its lifecycle</strong>: close it through
 * try-with-resources to release the socket. Consuming to exhaustion
 * lets the implementation close cleanly when the server sends its
 * final {@code "done": true} frame.
 */
public interface StreamingLlmApi {

    /**
     * Lazy stream of {@link StreamEvent}s. Implementations must not
     * trigger an HTTP round-trip before a terminal operation runs on
     * the stream.
     */
    Stream<StreamEvent> streamEvents(String prompt, String model);

    /**
     * Convenience wrapper: drops {@link StreamEvent.Thinking} events
     * and returns only the answer-facing token text.
     */
    default Stream<String> streamGenerate(String prompt, String model) {
        return streamEvents(prompt, model)
                .filter(event -> event instanceof StreamEvent.Token)
                .map(event -> ((StreamEvent.Token) event).text());
    }
}
