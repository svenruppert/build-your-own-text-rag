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
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * {@link StreamingLlmApi} backed by Ollama's {@code POST /api/generate}
 * with {@code "stream": true}.
 *
 * <h2>Wire format</h2>
 * Ollama returns newline-delimited JSON (NDJSON):
 * <pre>{@code
 * {"response": "Hel", "done": false}
 * {"response": "lo",  "done": false}
 * {"response": "",    "done": true}
 * }</pre>
 * Each line is parsed; non-empty {@code response} fields are yielded
 * as tokens, the {@code "done": true} line ends the stream.
 *
 * <h2>Resources</h2>
 * The returned {@link Stream} is backed by the live HTTP body stream;
 * closing it cancels the request. Callers should consume it inside a
 * try-with-resources or equivalent so the socket is always released.
 * The {@link DefaultGenerator} does this.
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
    public Stream<String> streamGenerate(String prompt, String model) {
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(model, "model");

        ObjectNode payload = objectMapper.createObjectNode()
                .put("model", model)
                .put("prompt", prompt)
                .put("stream", true);
        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (RuntimeException e) {
            logger().warn("failed to serialise streaming request: {}", e.getMessage());
            return Stream.empty();
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + "/api/generate"))
                .timeout(config.timeout())
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<Stream<String>> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
        } catch (IOException e) {
            logger().warn("streaming transport failure: {}", e.getMessage());
            return Stream.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger().warn("streaming interrupted during handshake");
            return Stream.empty();
        }
        if (response.statusCode() != 200) {
            logger().warn("streaming: unexpected status {} from {}",
                    response.statusCode(), request.uri());
            // Closing the body stream releases the connection.
            response.body().close();
            return Stream.empty();
        }

        // takeWhile fires its predicate BEFORE passing the element through.
        // We want to include the element on which we see {"done": true} --
        // a terminal flag accompanied by a final chunk of text. The
        // sentinel flips on the terminal frame; the next iteration stops
        // the stream.
        AtomicBoolean terminal = new AtomicBoolean(false);
        return response.body()
                .map(this::parseLine)
                .filter(Objects::nonNull)
                .takeWhile(frame -> !terminal.get())
                .map(frame -> {
                    if (frame.done()) terminal.set(true);
                    return frame.response();
                })
                .filter(token -> token != null && !token.isEmpty())
                .onClose(() -> response.body().close());
    }

    private OllamaFrame parseLine(String line) {
        if (line == null || line.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(line);
            JsonNode responseNode = root.path("response");
            String token = responseNode.isString() ? responseNode.asString() : "";
            boolean done = root.path("done").asBoolean(false);
            return new OllamaFrame(token, done);
        } catch (RuntimeException e) {
            logger().warn("malformed stream frame: {}", truncate(line));
            return null;
        }
    }

    private static String truncate(String s) {
        return s.length() > 120 ? s.substring(0, 120) + "..." : s;
    }

    /** Single frame in the Ollama streaming protocol. */
    private record OllamaFrame(String response, boolean done) {
    }
}
