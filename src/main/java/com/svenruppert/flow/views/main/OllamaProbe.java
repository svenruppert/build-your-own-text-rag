package com.svenruppert.flow.views.main;

import com.svenruppert.dependencies.core.logger.HasLogger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Tiny diagnostic client for the dashboard's "System" zone. Hits
 * {@code /api/version} and {@code /api/tags} on the configured
 * Ollama base URL and returns plain-Java shapes; every call swallows
 * I/O and parsing errors and returns {@link Optional#empty()}.
 *
 * <p>Kept deliberately outside {@link com.svenruppert.flow.views.module01
 * .DefaultLlmClient} so the dashboard's 2-second timeouts and the lab
 * views' 60-second operational timeouts do not bleed into each other.
 */
class OllamaProbe implements HasLogger {

  private static final Duration TIMEOUT = Duration.ofSeconds(2);

  private final String baseUrl;
  private final HttpClient http;
  private final ObjectMapper mapper = new ObjectMapper();

  OllamaProbe(String baseUrl) {
    this.baseUrl = baseUrl;
    this.http = HttpClient.newBuilder()
        .connectTimeout(TIMEOUT)
        .build();
  }

  String baseUrl() {
    return baseUrl;
  }

  /**
   * GET /api/version -- returns the version string on success, empty
   * on any failure (timeout, non-200, parse error, connection refused).
   */
  Optional<String> version() {
    try {
      HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/api/version"))
          .timeout(TIMEOUT)
          .GET().build();
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() != 200) return Optional.empty();
      JsonNode node = mapper.readTree(resp.body());
      JsonNode v = node.get("version");
      if (v == null) return Optional.empty();
      return Optional.of(v.asString());
    } catch (Exception e) {
      logger().warn("Ollama /api/version probe failed for {}: {}", baseUrl, e.toString());
      return Optional.empty();
    }
  }

  /**
   * GET /api/tags -- returns a map from model name to the first 12
   * characters of its digest. Empty Optional signals a failed call;
   * an empty map signals "reachable but no models installed".
   */
  Optional<Map<String, String>> localModels() {
    try {
      HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/api/tags"))
          .timeout(TIMEOUT)
          .GET().build();
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() != 200) return Optional.empty();
      JsonNode node = mapper.readTree(resp.body());
      JsonNode models = node.get("models");
      if (models == null || !models.isArray()) return Optional.of(Map.of());
      Map<String, String> out = new LinkedHashMap<>();
      for (JsonNode m : models) {
        JsonNode nameNode = m.get("name");
        if (nameNode == null) continue;
        String name = nameNode.asString();
        JsonNode digestNode = m.get("digest");
        String digest = (digestNode == null) ? "" : digestNode.asString();
        String short12 = digest.length() > 12 ? digest.substring(0, 12) : digest;
        out.put(name, short12);
      }
      return Optional.of(out);
    } catch (Exception e) {
      logger().warn("Ollama /api/tags probe failed for {}: {}", baseUrl, e.toString());
      return Optional.empty();
    }
  }
}
