package com.svenruppert.flow.views.module01;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test that only runs when {@code -Dworkshop.live=true} is set.
 * It exercises a real, locally running Ollama server at
 * {@code http://localhost:11434}.
 *
 * <p>Tagged {@code "live-ollama"} so CI environments can exclude it
 * with {@code -DexcludedGroups=live-ollama}.
 */
@Tag("live-ollama")
@EnabledIfSystemProperty(named = "workshop.live", matches = "true")
class LiveOllamaSmokeTest {

    @Test
    @DisplayName("local Ollama answers /api/tags with at least one model")
    void canReachLocalOllamaAndListModels() {
        LlmClient client = DefaultLlmClient.withDefaults();
        assertTrue(client.isAvailable(),
                "Ollama must be running at localhost:11434 for this smoke test");

        Optional<List<String>> models = client.listModels();
        assertTrue(models.isPresent(), "listModels should return a list");
        assertFalse(models.get().isEmpty(),
                "at least one model must be pulled locally (try: `ollama pull llama3.2`)");
    }
}
