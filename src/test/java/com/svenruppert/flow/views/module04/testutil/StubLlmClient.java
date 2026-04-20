package com.svenruppert.flow.views.module04.testutil;

import com.svenruppert.flow.views.module01.LlmClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Test double for {@link LlmClient} with canned responses. Keeps tests
 * free of any network plumbing or Jackson parsing.
 *
 * <p>Usage:
 * <pre>{@code
 * var llm = new StubLlmClient()
 *     .withEmbed("query text", new float[]{1f, 0f, 0f})
 *     .withGenerate("prompt containing XYZ", "7");
 * }</pre>
 */
public final class StubLlmClient implements LlmClient {

    private final Map<String, float[]> embeddings = new HashMap<>();
    private final Map<String, String> generations = new HashMap<>();
    private final List<GenerateCall> generateCalls = new ArrayList<>();
    private boolean available = true;

    public StubLlmClient withEmbed(String text, float[] vector) {
        embeddings.put(text, vector);
        return this;
    }

    public StubLlmClient withGenerate(String promptFragment, String reply) {
        generations.put(promptFragment, reply);
        return this;
    }

    public StubLlmClient unavailable() {
        this.available = false;
        return this;
    }

    public List<GenerateCall> generateCalls() {
        return List.copyOf(generateCalls);
    }

    @Override
    public Optional<List<String>> listModels() {
        return Optional.of(List.of("stub-model"));
    }

    @Override
    public Optional<float[]> embed(String text, String model) {
        if (!available) return Optional.empty();
        float[] v = embeddings.get(text);
        return v == null ? Optional.empty() : Optional.of(v.clone());
    }

    @Override
    public Optional<String> generate(String prompt, String model) {
        generateCalls.add(new GenerateCall(prompt, model));
        // Fragment match: first key whose text appears in the prompt wins.
        for (var entry : generations.entrySet()) {
            if (prompt.contains(entry.getKey())) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> generate(String prompt, String model, List<String> contextDocuments) {
        return generate(prompt, model);
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    public record GenerateCall(String prompt, String model) {
    }
}
