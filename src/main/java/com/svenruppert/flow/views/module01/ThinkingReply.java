package com.svenruppert.flow.views.module01;

import java.util.Objects;

/**
 * A non-streaming LLM reply that surfaces a reasoning model's
 * thinking alongside the user-facing answer.
 *
 * <p>Ollama exposes thinking on the {@code /api/generate} endpoint
 * via a separate {@code "thinking"} field in the JSON response when
 * the request carries {@code "think": true} and the model supports
 * it (deepseek-r1, qwen3-*thinking*, gpt-oss-*, ...).
 *
 * @param response user-facing answer text; never {@code null}
 * @param thinking internal reasoning text emitted by a thinking model;
 *                 empty string when the model does not think or when
 *                 the server does not expose the separate field
 */
public record ThinkingReply(String response, String thinking) {

    public ThinkingReply {
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(thinking, "thinking");
    }

    /** Convenience factory for callers that have no thinking to carry. */
    public static ThinkingReply ofResponseOnly(String response) {
        return new ThinkingReply(response, "");
    }
}
