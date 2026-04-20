package com.svenruppert.flow.views.module05;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.views.module01.LlmConfig;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * {@link StreamingLlmApi} backed by Ollama's
 * {@code POST /api/generate} with {@code "stream": true} and
 * {@code "think": true}.
 *
 * <h2>Wire format</h2>
 * Ollama returns newline-delimited JSON (NDJSON). Each frame carries a
 * {@code response} and, for thinking models, a {@code thinking} field:
 * <pre>{@code
 * {"response": "Hel", "thinking": "",     "done": false}
 * {"response": "",    "thinking": "Let",  "done": false}
 * {"response": "",    "thinking": " me ", "done": false}
 * {"response": "lo",  "thinking": "",     "done": false}
 * {"response": "",    "thinking": "",     "done": true}
 * }</pre>
 * Each non-empty {@code response} is yielded as {@link StreamEvent.Token};
 * each non-empty {@code thinking} becomes {@link StreamEvent.Thinking}.
 * The first frame carrying {@code "done": true} ends the stream.
 *
 * <h2>Ollama version note</h2>
 * The {@code think} parameter and the separate {@code thinking} field
 * are recent additions. Older Ollama builds ignore unknown parameters
 * and omit the field; the resulting stream carries only Token events,
 * which is the existing pre-thinking behaviour. Models that do not
 * support reasoning simply leave the thinking field empty.
 *
 * <h2>Resources</h2>
 * The returned {@link Stream} is backed by the live HTTP body stream;
 * closing it cancels the request. Callers should consume it inside a
 * try-with-resources or equivalent so the socket is always released.
 * {@link DefaultGenerator} does this.
 */
public final class OllamaStreamingApi implements StreamingLlmApi, HasLogger {

    private final LlmConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OllamaStreamingApi(LlmConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.timeout())
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Stream<StreamEvent> streamEvents(String prompt, String model) {
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(model, "model");

        // Recent Ollama builds validate capability: a non-thinking model
        // (gemma, llama3.2, mistral, ...) returns HTTP 400 when the
        // request carries "think": true. Try with thinking first so
        // reasoning models still emit the dedicated channel, and fall
        // back to a plain request on 400.
        HttpResponse<Stream<String>> response = send(prompt, model, true);
        if (response != null && response.statusCode() == 400) {
            response.body().close();
            logger().info("streaming: model '{}' rejected think=true (400); "
                    + "retrying without thinking", model);
            response = send(prompt, model, false);
        }
        if (response == null) return Stream.empty();
        if (response.statusCode() != 200) {
            logger().warn("streaming: unexpected status {} from {}/api/generate",
                    response.statusCode(), config.baseUrl());
            response.body().close();
            return Stream.empty();
        }

        final HttpResponse<Stream<String>> live = response;
        // The done-true frame may still carry a final response/thinking
        // chunk. Sentinel lets us include that frame's events and stop
        // on the next iteration.
        AtomicBoolean terminal = new AtomicBoolean(false);
        return live.body()
                .map(this::parseLine)
                .filter(Objects::nonNull)
                .takeWhile(frame -> !terminal.get())
                .flatMap(frame -> {
                    if (frame.done()) terminal.set(true);
                    List<StreamEvent> events = new ArrayList<>(2);
                    if (!frame.thinking().isEmpty()) {
                        events.add(new StreamEvent.Thinking(frame.thinking()));
                    }
                    if (!frame.response().isEmpty()) {
                        events.add(new StreamEvent.Token(frame.response()));
                    }
                    return events.stream();
                })
                .onClose(() -> live.body().close());
    }

    /**
     * Posts the generation request to Ollama, toggling the {@code think}
     * field on or off. Returns {@code null} on transport failure so the
     * caller can fall through to an empty stream.
     */
    private HttpResponse<Stream<String>> send(String prompt, String model, boolean think) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("model", model)
                .put("prompt", prompt)
                .put("stream", true);
        if (think) payload.put("think", true);
        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (RuntimeException e) {
            logger().warn("failed to serialise streaming request: {}", e.getMessage());
            return null;
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + "/api/generate"))
                .timeout(config.timeout())
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
        } catch (IOException e) {
            logger().warn("streaming transport failure: {}", e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger().warn("streaming interrupted during handshake");
            return null;
        }
    }

    private OllamaFrame parseLine(String line) {
        if (line == null || line.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(line);
            String token = stringOrEmpty(root.path("response"));
            String think = stringOrEmpty(root.path("thinking"));
            boolean done = root.path("done").asBoolean(false);
            return new OllamaFrame(token, think, done);
        } catch (RuntimeException e) {
            logger().warn("malformed stream frame: {}", truncate(line));
            return null;
        }
    }

    private static String stringOrEmpty(JsonNode node) {
        return node.isString() ? node.asString() : "";
    }

    private static String truncate(String s) {
        return s.length() > 120 ? s.substring(0, 120) + "..." : s;
    }

    /** Single parsed frame in the Ollama streaming protocol. */
    private record OllamaFrame(String response, String thinking, boolean done) {
    }
}
