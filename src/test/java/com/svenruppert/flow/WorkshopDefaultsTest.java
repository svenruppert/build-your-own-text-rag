package com.svenruppert.flow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopDefaultsTest {

    @Test
    @DisplayName("workshop model defaults are pinned")
    void modelDefaults() {
        assertEquals("nomic-embed-text-v2-moe",
                WorkshopDefaults.DEFAULT_EMBEDDING_MODEL);
        assertEquals("gemma4:e4b",
                WorkshopDefaults.DEFAULT_GENERATION_MODEL);
    }

    @Test
    @DisplayName("diagnostic model list contains the defaults")
    void diagnosticModelsContainDefaults() {
        assertTrue(WorkshopDefaults.REQUIRED_MODELS.contains(
                WorkshopDefaults.DEFAULT_EMBEDDING_MODEL));
        assertTrue(WorkshopDefaults.REQUIRED_MODELS.contains(
                WorkshopDefaults.DEFAULT_GENERATION_MODEL));
    }

    @Test
    @DisplayName("model preference chooses the pinned defaults when available")
    void preferPinnedDefaults() {
        assertEquals("nomic-embed-text-v2-moe",
                WorkshopDefaults.preferredEmbeddingModel(List.of(
                        "mxbai-embed-large", "nomic-embed-text-v2-moe")));
        assertEquals("gemma4:e4b",
                WorkshopDefaults.preferredGenerationModel(List.of(
                        "llama3.2", "gemma4:e4b")));
    }

    @Test
    @DisplayName("model preference keeps sensible fallbacks")
    void preferFallbackFamilies() {
        assertEquals("mxbai-embed-large",
                WorkshopDefaults.preferredEmbeddingModel(List.of(
                        "phi3", "mxbai-embed-large")));
        assertEquals("qwen3:14b",
                WorkshopDefaults.preferredGenerationModel(List.of(
                        "phi3", "qwen3:14b")));
    }
}
