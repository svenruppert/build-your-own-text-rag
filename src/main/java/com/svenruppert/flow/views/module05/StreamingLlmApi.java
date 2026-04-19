package com.svenruppert.flow.views.module05;

import java.util.stream.Stream;

/**
 * HTTP-backed streaming completion: one token (or small batch of
 * tokens) per stream element.
 *
 * <p>The returned {@link Stream} is backed by a live HTTP connection;
 * <strong>the caller owns its lifecycle</strong>. Closing the stream
 * (preferably through try-with-resources) cancels the request and
 * releases the connection. Consuming to exhaustion lets the
 * implementation close cleanly when the server sends its final
 * {@code "done": true} frame.
 *
 * <p>Implementations are lazy: no HTTP round-trip happens until the
 * caller triggers a terminal operation on the stream.
 */
public interface StreamingLlmApi {

    /**
     * @param prompt full prompt text
     * @param model  Ollama model name
     * @return a sequential stream of tokens; empty if the server is
     *         unreachable or returns a non-200 status
     */
    Stream<String> streamGenerate(String prompt, String model);
}
