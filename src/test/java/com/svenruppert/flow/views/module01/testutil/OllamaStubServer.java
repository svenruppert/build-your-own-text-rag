package com.svenruppert.flow.views.module01.testutil;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-JVM stub for the Ollama HTTP API, backed by {@link HttpServer} from
 * the JDK. No third-party test library; a workshop participant can read
 * this class top-to-bottom in a couple of minutes.
 *
 * <p>Register canned responses per path with {@link #respondWith}, then
 * inspect {@link #recordedRequests()} after the client call to verify
 * the outgoing payload.
 */
public final class OllamaStubServer implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, StubResponse> routes = new ConcurrentHashMap<>();
    private final List<RecordedRequest> recorded =
            Collections.synchronizedList(new ArrayList<>());

    public OllamaStubServer() throws IOException {
        // Port 0 -> let the kernel pick a free ephemeral port.
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            byte[] requestBody = exchange.getRequestBody().readAllBytes();

            Map<String, List<String>> headers = new HashMap<>();
            exchange.getRequestHeaders().forEach((k, v) -> headers.put(k, List.copyOf(v)));
            recorded.add(new RecordedRequest(
                    exchange.getRequestMethod(),
                    path,
                    Collections.unmodifiableMap(headers),
                    new String(requestBody, StandardCharsets.UTF_8)));

            StubResponse stub = routes.get(path);
            if (stub == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            byte[] bodyBytes = stub.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(stub.status(), bodyBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bodyBytes);
            }
        });
    }

    public OllamaStubServer start() {
        server.start();
        return this;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + port();
    }

    /**
     * Registers a canned response for the given path.
     *
     * @return {@code this}, for fluent chaining inside tests
     */
    public OllamaStubServer respondWith(String path, int status, String jsonBody) {
        routes.put(path, new StubResponse(status, jsonBody));
        return this;
    }

    public List<RecordedRequest> recordedRequests() {
        synchronized (recorded) {
            return List.copyOf(recorded);
        }
    }

    /**
     * Returns the most recent request observed for {@code path}.
     *
     * @throws IllegalStateException if the stub never received a request for that path
     */
    public RecordedRequest lastRequestTo(String path) {
        List<RecordedRequest> snapshot = recordedRequests();
        for (int i = snapshot.size() - 1; i >= 0; i--) {
            if (snapshot.get(i).path().equals(path)) {
                return snapshot.get(i);
            }
        }
        throw new IllegalStateException("No request recorded for path " + path);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private record StubResponse(int status, String body) {
    }

    public record RecordedRequest(
            String method,
            String path,
            Map<String, List<String>> headers,
            String body) {
    }
}
