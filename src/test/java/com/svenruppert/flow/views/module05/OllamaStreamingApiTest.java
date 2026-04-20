package com.svenruppert.flow.views.module05;

import com.svenruppert.flow.views.module01.LlmConfig;
import com.svenruppert.flow.views.module01.testutil.OllamaStubServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link OllamaStreamingApi} against the in-JVM
 * {@code OllamaStubServer} from module 1's test utilities. The stub
 * server simply returns a fixed body; newline-delimited JSON in that
 * body is split by {@link java.net.http.HttpResponse.BodyHandlers#ofLines()}
 * on the client side, which is exactly what Ollama sends in production.
 */
class OllamaStreamingApiTest {

    private OllamaStubServer stub;
    private OllamaStreamingApi api;

    @BeforeEach
    void setUp() throws IOException {
        stub = new OllamaStubServer().start();
        api = new OllamaStreamingApi(new LlmConfig(
                stub.baseUrl(), Duration.ofSeconds(5), "nomic-embed-text"));
    }

    @AfterEach
    void tearDown() {
        if (stub != null) stub.close();
    }

    @Test
    @DisplayName("request body carries model, prompt and stream=true")
    void streamGenerate_sendsCorrectJsonBodyWithStreamTrue() {
        stub.respondWith("/api/generate", 200,
                "{\"response\":\"x\",\"done\":false}\n{\"response\":\"\",\"done\":true}\n");

        try (Stream<String> tokens = api.streamGenerate("how are you?", "llama3.2")) {
            tokens.toList();
        }

        OllamaStubServer.RecordedRequest req = stub.lastRequestTo("/api/generate");
        assertEquals("POST", req.method());
        String body = req.body();
        assertTrue(body.contains("\"model\""));
        assertTrue(body.contains("llama3.2"));
        assertTrue(body.contains("\"prompt\""));
        assertTrue(body.contains("how are you?"));
        assertTrue(body.contains("\"stream\""));
        assertTrue(body.contains("true"));
    }

    @Test
    @DisplayName("yields response tokens in the order the server sent them")
    void streamGenerate_yieldsResponseTokensInOrder() {
        stub.respondWith("/api/generate", 200, """
                {"response":"Hel","done":false}
                {"response":"lo","done":false}
                {"response":" ","done":false}
                {"response":"world","done":false}
                {"response":"","done":true}
                """);
        try (Stream<String> tokens = api.streamGenerate("q", "m")) {
            assertEquals(List.of("Hel", "lo", " ", "world"), tokens.toList());
        }
    }

    @Test
    @DisplayName("stops the stream on done=true, ignoring any later frames")
    void streamGenerate_endsStreamOnDoneTrue() {
        // The stream must close at the done-true frame. Any frame after it
        // should be ignored even if the server happened to send more.
        stub.respondWith("/api/generate", 200, """
                {"response":"A","done":false}
                {"response":"","done":true}
                {"response":"B","done":false}
                """);
        try (Stream<String> tokens = api.streamGenerate("q", "m")) {
            assertEquals(List.of("A"), tokens.toList());
        }
    }

    @Test
    @DisplayName("returns an empty stream when Ollama responds with HTTP 500")
    void streamGenerate_returnsEmptyStreamOnHttp500() {
        stub.respondWith("/api/generate", 500, "internal error");
        try (Stream<String> tokens = api.streamGenerate("q", "m")) {
            assertTrue(tokens.toList().isEmpty());
        }
    }
}
