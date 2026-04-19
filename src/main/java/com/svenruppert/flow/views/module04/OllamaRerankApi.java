package com.svenruppert.flow.views.module04;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.views.module01.LlmConfig;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
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

/**
 * HTTP client for Ollama's dedicated reranking endpoint.
 *
 * <p>Sends
 * <pre>{@code
 * POST /api/rerank
 * { "model": "...",
 *   "query": "...",
 *   "documents": [ "...", "..." ],
 *   "top_n": N }
 * }</pre>
 * and parses the response
 * <pre>{@code
 * { "results": [
 *     { "index": 2, "relevance_score": 0.87 },
 *     { "index": 0, "relevance_score": 0.74 }
 * ]}
 * }</pre>
 *
 * <h2>Version requirement</h2>
 * The {@code /api/rerank} endpoint is a recent addition to Ollama; any
 * build that predates it responds with HTTP 404. In that case this
 * class logs a single warning and returns an empty list. {@link BgeReranker}
 * treats the empty list as "pass candidates through unchanged" --
 * so a stale Ollama build degrades gracefully rather than throwing.
 */
public final class OllamaRerankApi implements RerankApi, HasLogger {

    private final LlmConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OllamaRerankApi(LlmConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.timeout())
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<RerankScore> rerank(String model, String query,
                                    List<String> documents, int topN) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(documents, "documents");
        if (documents.isEmpty() || topN <= 0) return List.of();

        ObjectNode payload = objectMapper.createObjectNode()
                .put("model", model)
                .put("query", query)
                .put("top_n", topN);
        ArrayNode docs = payload.putArray("documents");
        for (String d : documents) docs.add(d);

        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl() + "/api/rerank"))
                    .timeout(config.timeout())
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() == 404) {
                logger().warn("Ollama /api/rerank not available at {} "
                        + "(404); this build does not expose the rerank endpoint. "
                        + "BgeReranker will pass candidates through unchanged.",
                        config.baseUrl());
                return List.of();
            }
            if (response.statusCode() != 200) {
                logger().warn("rerank: unexpected status {} from {}",
                        response.statusCode(), request.uri());
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode results = root.path("results");
            if (!results.isArray()) {
                logger().warn("rerank: missing or non-array 'results' field");
                return List.of();
            }
            List<RerankScore> out = new ArrayList<>(results.size());
            for (JsonNode entry : results) {
                int index = entry.path("index").asInt(-1);
                double score = entry.path("relevance_score").asDouble(Double.NaN);
                if (index < 0 || Double.isNaN(score)) continue;
                out.add(new RerankScore(index, score));
            }
            return List.copyOf(out);
        } catch (IOException e) {
            logger().warn("rerank: transport failure: {}", e.getMessage());
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger().warn("rerank: interrupted");
            return List.of();
        } catch (RuntimeException e) {
            logger().warn("rerank: parse/runtime failure: {}", e.getMessage());
            return List.of();
        }
    }
}
