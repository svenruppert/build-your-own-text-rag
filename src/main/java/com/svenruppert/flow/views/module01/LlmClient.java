package com.svenruppert.flow.views.module01;

import java.util.List;
import java.util.Optional;

/**
 * Abstraction over a local large language model server (Ollama).
 *
 * <p>Implementations speak raw HTTP against the Ollama REST API:
 * <ul>
 *   <li>{@code GET  /api/tags}     -- list locally available models</li>
 *   <li>{@code POST /api/embed}    -- compute an embedding for a text</li>
 *   <li>{@code POST /api/generate} -- perform a single-turn completion</li>
 * </ul>
 *
 * <p>Every fallible read-operation returns an {@link Optional}: on any
 * transport, protocol or parsing failure the implementation is expected
 * to log the cause and return {@link Optional#empty()} rather than
 * throwing. This keeps callers (the Vaadin view, subsequent modules)
 * free from checked-exception noise.
 */
public interface LlmClient {

    /**
     * Lists the model names known to the Ollama server.
     *
     * <p>Ollama responds with
     * <pre>{@code { "models": [ { "name": "gemma4:e4b" }, ... ] }}</pre>
     *
     * @return the model names in declaration order, or {@link Optional#empty()}
     *         if the server cannot be reached or the payload cannot be parsed
     */
    Optional<List<String>> listModels();

    /**
     * Computes an embedding vector for the given text.
     *
     * <p>Request payload:  {@code { "model": "...", "input": "..." }}<br>
     * Response payload:   {@code { "embeddings": [ [ ...floats... ] ] }}
     *
     * @param text  the input text, non-{@code null}
     * @param model the embedding model name
     *              (for example {@code nomic-embed-text-v2-moe})
     * @return the embedding vector, or {@link Optional#empty()} on failure
     */
    Optional<float[]> embed(String text, String model);

    /**
     * Performs a single-turn completion -- stage one of this module's
     * teaching arc: a naive prompt, with no context.
     *
     * <p>Request payload:
     * {@code { "model": "...", "prompt": "...", "stream": false }}<br>
     * Response payload: {@code { "response": "..." }}
     *
     * @param prompt the user prompt, non-{@code null}
     * @param model  the generative model name
     * @return the model's reply, or {@link Optional#empty()} on failure
     */
    Optional<String> generate(String prompt, String model);

    /**
     * Performs a completion with the given documents concatenated into
     * the prompt -- stage two of the teaching arc: "just stuff the
     * documents in and see what happens".
     *
     * <p>The documents are expected to appear in the constructed prompt
     * <em>before</em> the user's question. The exact layout (delimiters,
     * section titles, ordering) is an implementation choice; the module's
     * test suite only verifies that every document's content and the
     * user question appear, and that the documents precede the question.
     *
     * <p>This deliberately-crude approach motivates every later module:
     * chunking, embeddings, retrieval. For now, whole documents go in
     * verbatim.
     *
     * @param prompt           the user prompt
     * @param model            the generative model name
     * @param contextDocuments the documents to prepend to the prompt; may be empty
     * @return the model's reply, or {@link Optional#empty()} on failure
     */
    Optional<String> generate(String prompt, String model, List<String> contextDocuments);

    /**
     * Cheap liveness probe: returns {@code true} iff the Ollama server
     * answers a {@code GET /api/tags} with HTTP 200. Used by the UI to
     * decide whether to surface an error before sending a prompt.
     */
    boolean isAvailable();

    /**
     * Thinking-aware completion.
     *
     * <p>Sends a non-streaming {@code /api/generate} call with
     * {@code "think": true} and returns both the user-facing
     * {@code response} and any reasoning tokens the model emits on
     * the separate {@code thinking} field.
     *
     * <p>The default implementation falls back to {@link #generate}
     * and returns an empty thinking string -- good enough for
     * non-Ollama backends or older implementations, but a true
     * reasoning-aware client overrides this.
     *
     * @return a {@link ThinkingReply}, or {@link Optional#empty()} on
     *         transport / parse failure
     */
    default Optional<ThinkingReply> generateWithThinking(String prompt, String model) {
        return generate(prompt, model).map(ThinkingReply::ofResponseOnly);
    }
}
