package com.svenruppert.flow.views.module01;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable configuration for an {@link LlmClient}.
 *
 * @param baseUrl                base URL of the Ollama server, without trailing slash
 *                               (for example {@code http://localhost:11434})
 * @param timeout                the per-request timeout applied to both the connection
 *                               attempt and the overall request
 * @param defaultEmbeddingModel  model name used when callers do not specify one
 */
public record LlmConfig(String baseUrl, Duration timeout, String defaultEmbeddingModel) {

    public LlmConfig {
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(defaultEmbeddingModel, "defaultEmbeddingModel");
    }

    /**
     * Sensible defaults for a locally running Ollama: {@code localhost:11434},
     * 60s timeout, {@code nomic-embed-text} as the embedding model.
     */
    public static LlmConfig defaults() {
        return new LlmConfig(
                "http://localhost:11434",
                Duration.ofSeconds(60),
                "nomic-embed-text");
    }
}
