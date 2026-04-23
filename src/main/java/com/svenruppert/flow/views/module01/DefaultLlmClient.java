package com.svenruppert.flow.views.module01;

import com.svenruppert.dependencies.core.logger.HasLogger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Minimal Ollama HTTP client built on {@link HttpClient} (JDK) and
 * Jackson's {@link ObjectMapper}.
 *
 * <p>The reference implementation for module 1. Kept deliberately close
 * to the code a participant is expected to write: no retries, no
 * connection pooling, no caching -- just the raw HTTP round-trip and
 * a handful of {@link Optional}-returning methods.
 *
 * <p>Every network-facing method swallows I/O, protocol and parsing
 * failures, logs the cause via {@link HasLogger#logger()} and returns
 * {@link Optional#empty()}. This keeps the calling Vaadin view free of
 * checked-exception handling.
 *
 * <h2>Ollama JSON contracts</h2>
 * <ul>
 *   <li>{@code GET  /api/tags}      -> {@code { "models": [ { "name": "..." }, ... ] }}</li>
 *   <li>{@code POST /api/embed}     -- body {@code { "model": "...", "input": "..." }},
 *       response {@code { "embeddings": [ [ floats ] ] }}</li>
 *   <li>{@code POST /api/generate}  -- body {@code { "model": "...", "prompt": "...", "stream": false }},
 *       response {@code { "response": "..." }}</li>
 * </ul>
 */
public class DefaultLlmClient implements LlmClient, HasLogger {

    private final LlmConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DefaultLlmClient(LlmConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.timeout())
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Shortcut for {@code new DefaultLlmClient(LlmConfig.defaults())}.
     * Centralises the five call sites in the view layer that previously
     * spelled the config construction out by hand.
     */
    public static DefaultLlmClient withDefaults() {
        return new DefaultLlmClient(LlmConfig.defaults());
    }

    @Override
    public Optional<List<String>> listModels() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + "/api/tags"))
                .timeout(config.timeout())
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    // Decode explicitly as UTF-8: Ollama does not always send a charset
                    // parameter and the JDK would otherwise fall back to ISO-8859-1.
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                logger().warn("listModels: unexpected status {} from {}",
                        response.statusCode(), request.uri());
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode models = root.path("models");
            if (!models.isArray()) {
                logger().warn("listModels: missing or non-array 'models' field");
                return Optional.empty();
            }
            List<String> names = new ArrayList<>();
            for (JsonNode node : models) {
                JsonNode name = node.path("name");
                if (name.isString()) {
                    names.add(name.asString());
                }
            }
            return Optional.of(List.copyOf(names));
        } catch (IOException e) {
            logger().warn("listModels: transport failure: {}", e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger().warn("listModels: interrupted");
            return Optional.empty();
        } catch (RuntimeException e) {
            // Jackson 3's JacksonException is a RuntimeException -- covers
            // malformed JSON and unexpected node types.
            logger().warn("listModels: parse/runtime failure: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<float[]> embed(String text, String model) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(model, "model");

        ObjectNode payload = objectMapper.createObjectNode()
                .put("model", model)
                .put("input", text);
        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl() + "/api/embed"))
                    .timeout(config.timeout())
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                logger().warn("embed: unexpected status {} from {}",
                        response.statusCode(), request.uri());
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode embeddings = root.path("embeddings");
            if (!embeddings.isArray() || embeddings.isEmpty()) {
                logger().warn("embed: missing or empty 'embeddings' array");
                return Optional.empty();
            }
            JsonNode first = embeddings.get(0);
            if (!first.isArray() || first.isEmpty()) {
                logger().warn("embed: 'embeddings[0]' is not a non-empty array");
                return Optional.empty();
            }
            float[] vector = new float[first.size()];
            for (int i = 0; i < first.size(); i++) {
                vector[i] = (float) first.get(i).asDouble();
            }
            return Optional.of(vector);
        } catch (IOException e) {
            logger().warn("embed: transport failure: {}", e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger().warn("embed: interrupted");
            return Optional.empty();
        } catch (RuntimeException e) {
            logger().warn("embed: parse/runtime failure: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> generate(String prompt, String model) {
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(model, "model");

        ObjectNode payload = objectMapper.createObjectNode()
                .put("model", model)
                .put("prompt", prompt)
                // Non-streaming mode keeps the reply in one JSON object.
                // Streaming (Ollama's default) would emit newline-delimited JSON,
                // which this module deliberately does not handle.
                .put("stream", false);
        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl() + "/api/generate"))
                    .timeout(config.timeout())
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                logger().warn("generate: unexpected status {} from {}",
                        response.statusCode(), request.uri());
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode responseText = root.path("response");
            if (!responseText.isTextual()) {
                logger().warn("generate: missing or non-textual 'response' field");
                return Optional.empty();
            }
            return Optional.of(responseText.asText());
        } catch (IOException e) {
            logger().warn("generate: transport failure: {}", e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger().warn("generate: interrupted");
            return Optional.empty();
        } catch (RuntimeException e) {
            logger().warn("generate: parse/runtime failure: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> generate(String prompt, String model, List<String> contextDocuments) {
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(model, "model");
        if (contextDocuments == null || contextDocuments.isEmpty()) {
            return generate(prompt, model);
        }

        // Stage-two prompting: splice every document into a context block
        // that precedes the user question. Crude on purpose -- the whole
        // point of later modules is to replace this with retrieval.
        StringBuilder composed = new StringBuilder();
        composed.append("You are a helpful assistant. Use the following context")
                .append(" to answer the user's question. If the context does not")
                .append(" contain the answer, say so honestly.\n\n");
        composed.append("=== CONTEXT ===\n");
        int index = 1;
        for (String document : contextDocuments) {
            composed.append("--- Document ").append(index++).append(" ---\n");
            composed.append(document).append("\n\n");
        }
        composed.append("=== QUESTION ===\n");
        composed.append(prompt);
        return generate(composed.toString(), model);
    }

    @Override
    public boolean isAvailable() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + "/api/tags"))
                .timeout(config.timeout())
                .GET()
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Thinking-aware completion. Sends
     * {@code POST /api/generate} with {@code "think": true} so that
     * reasoning models (deepseek-r1, qwen3-*thinking*, gpt-oss-*) put
     * their chain of thought on the dedicated {@code "thinking"} field
     * instead of leaving it out or mixing it into the response text.
     *
     * <p>Older Ollama builds ignore the {@code think} parameter and
     * omit the {@code thinking} field; the returned
     * {@link ThinkingReply} then carries an empty {@code thinking}
     * string, which downstream callers treat as "no reasoning
     * observed". Non-thinking models behave the same way.
     */
    @Override
    public Optional<ThinkingReply> generateWithThinking(String prompt, String model) {
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(model, "model");

        ObjectNode payload = objectMapper.createObjectNode()
                .put("model", model)
                .put("prompt", prompt)
                .put("stream", false)
                .put("think", true);
        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl() + "/api/generate"))
                    .timeout(config.timeout())
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                logger().warn("generateWithThinking: unexpected status {} from {}",
                        response.statusCode(), request.uri());
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            String responseText = textOrEmpty(root.path("response"));
            String thinkingText = textOrEmpty(root.path("thinking"));
            return Optional.of(new ThinkingReply(responseText, thinkingText));
        } catch (IOException e) {
            logger().warn("generateWithThinking: transport failure: {}", e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger().warn("generateWithThinking: interrupted");
            return Optional.empty();
        } catch (RuntimeException e) {
            logger().warn("generateWithThinking: parse/runtime failure: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Reads a string JSON node tolerating both Jackson 3's
     * {@code isString}/{@code asString} and the classic
     * {@code isTextual}/{@code asText} names.
     */
    private static String textOrEmpty(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return "";
        if (node.isTextual()) return node.asText();
        return "";
    }
}
