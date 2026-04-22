package com.svenruppert.flow;

import java.util.List;
import java.util.function.Predicate;

/**
 * Workshop-wide defaults that must stay aligned across modules,
 * documentation and diagnostics.
 */
public final class WorkshopDefaults {

    public static final String DEFAULT_EMBEDDING_MODEL = "nomic-embed-text-v2-moe";
    public static final String DEFAULT_GENERATION_MODEL = "gemma4:e4b";

    public static final List<String> REQUIRED_MODELS = List.of(
            DEFAULT_GENERATION_MODEL,
            DEFAULT_EMBEDDING_MODEL,
            "bge-m3",
            "gemma4:31b");

    public static final List<String> OPTIONAL_MODELS = List.of(
            "embeddinggemma",
            "gemma3",
            "ministral-3",
            "qwen3:14b",
            "deepseek-r1",
            "llama3.2",
            "gemma",
            "qwen3",
            "nomic-embed-text");

    public static String preferredEmbeddingModel(List<String> models) {
        return preferredModel(models, DEFAULT_EMBEDDING_MODEL,
                model -> model.contains("embed"));
    }

    public static String preferredGenerationModel(List<String> models) {
        return preferredModel(models, DEFAULT_GENERATION_MODEL,
                model -> model.contains("gemma")
                        || model.contains("llama")
                        || model.contains("qwen"));
    }

    private static String preferredModel(List<String> models, String defaultModel,
                                         Predicate<String> fallbackMatcher) {
        if (models == null || models.isEmpty()) {
            return defaultModel;
        }
        if (models.contains(defaultModel)) {
            return defaultModel;
        }
        return models.stream()
                .filter(fallbackMatcher)
                .findFirst()
                .orElse(models.get(0));
    }

    private WorkshopDefaults() {
        throw new UnsupportedOperationException("WorkshopDefaults is not instantiable");
    }
}
