package com.svenruppert.flow.views.module01;

import com.svenruppert.flow.views.module01.testutil.OllamaStubServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Specification-style tests for {@link LlmClient}. They also act as the
 * behavioural brief for {@link DefaultLlmClient}.
 */
class DefaultLlmClientTest {

    private OllamaStubServer stub;
    private LlmClient client;

    @BeforeEach
    void setUp() throws IOException {
        stub = new OllamaStubServer().start();
        client = new DefaultLlmClient(new LlmConfig(
                stub.baseUrl(), Duration.ofSeconds(5), "nomic-embed-text"));
    }

    @AfterEach
    void tearDown() {
        if (stub != null) stub.close();
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = DefaultLlmClientTest.class.getResourceAsStream(
                "/module01/fixtures/" + name)) {
            assertNotNull(in, "missing fixture on test classpath: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ---------- listModels ----------------------------------------------

    @Test
    @DisplayName("listModels returns model names from /api/tags")
    void listModels_returnsModelNamesFromTags() throws IOException {
        stub.respondWith("/api/tags", 200, fixture("tags-response.json"));
        Optional<List<String>> models = client.listModels();
        assertTrue(models.isPresent(), "expected Optional with models");
        assertEquals(
                List.of("llama3.2", "qwen2.5:7b", "phi3", "nomic-embed-text"),
                models.get());
    }

    @Test
    @DisplayName("listModels returns empty Optional on HTTP 500")
    void listModels_returnsEmptyOptionalOnHttp500() {
        stub.respondWith("/api/tags", 500, "{}");
        assertTrue(client.listModels().isEmpty());
    }

    @Test
    @DisplayName("listModels returns empty Optional on malformed JSON")
    void listModels_returnsEmptyOptionalOnMalformedJson() {
        stub.respondWith("/api/tags", 200, "{ this is not JSON ");
        assertTrue(client.listModels().isEmpty());
    }

    // ---------- isAvailable ---------------------------------------------

    @Test
    @DisplayName("isAvailable is true when /api/tags returns 200")
    void isAvailable_trueOn200() {
        stub.respondWith("/api/tags", 200, "{\"models\": []}");
        assertTrue(client.isAvailable());
    }

    @Test
    @DisplayName("isAvailable is false when the server refuses the connection")
    void isAvailable_falseOnConnectionRefused() throws IOException {
        // Grab a free port, close it immediately, then point the client at
        // that now-unused port. The OS returns "connection refused" fast.
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }
        LlmClient unreachable = new DefaultLlmClient(new LlmConfig(
                "http://127.0.0.1:" + freePort,
                Duration.ofSeconds(1),
                "nomic-embed-text"));
        assertFalse(unreachable.isAvailable());
    }

    // ---------- embed (participant implements) --------------------------

    @Test
    @DisplayName("embed sends { model, input } as JSON to /api/embed")
    void embed_sendsCorrectJsonBody() throws IOException {
        stub.respondWith("/api/embed", 200, fixture("embed-response.json"));
        client.embed("hello world", "nomic-embed-text");
        OllamaStubServer.RecordedRequest request = stub.lastRequestTo("/api/embed");
        assertEquals("POST", request.method());
        String body = request.body();
        assertTrue(body.contains("\"model\""), "body must contain a 'model' field: " + body);
        assertTrue(body.contains("nomic-embed-text"),
                "body must carry the requested model: " + body);
        assertTrue(body.contains("\"input\""), "body must contain an 'input' field: " + body);
        assertTrue(body.contains("hello world"),
                "body must carry the input text: " + body);
    }

    @Test
    @DisplayName("embed parses embeddings[0] into a float[]")
    void embed_parsesEmbeddingVector() throws IOException {
        stub.respondWith("/api/embed", 200, fixture("embed-response.json"));
        Optional<float[]> vector = client.embed("hello", "nomic-embed-text");
        assertTrue(vector.isPresent(), "expected an embedding vector");
        assertEquals(768, vector.get().length,
                "nomic-embed-text yields 768-dimensional embeddings");
    }

    @Test
    @DisplayName("embed returns empty Optional on HTTP 500")
    void embed_returnsEmptyOnHttp500() {
        stub.respondWith("/api/embed", 500, "{}");
        assertTrue(client.embed("hello", "nomic-embed-text").isEmpty());
    }

    @Test
    @DisplayName("embed returns empty Optional when embeddings array is empty")
    void embed_returnsEmptyOnEmptyEmbeddings() {
        stub.respondWith("/api/embed", 200, "{\"embeddings\": []}");
        assertTrue(client.embed("hello", "nomic-embed-text").isEmpty());
    }

    // ---------- generate (participant implements) -----------------------

    @Test
    @DisplayName("generate sends { model, prompt, stream=false } as JSON to /api/generate")
    void generate_sendsCorrectJsonBody() throws IOException {
        stub.respondWith("/api/generate", 200, fixture("generate-response.json"));
        client.generate("What is the capital of France?", "llama3.2");
        OllamaStubServer.RecordedRequest request = stub.lastRequestTo("/api/generate");
        assertEquals("POST", request.method());
        String body = request.body();
        assertTrue(body.contains("\"model\""), "body must carry 'model': " + body);
        assertTrue(body.contains("llama3.2"), "body must name the model: " + body);
        assertTrue(body.contains("\"prompt\""), "body must carry 'prompt': " + body);
        assertTrue(body.contains("What is the capital of France?"),
                "body must carry the user prompt: " + body);
        assertTrue(body.contains("\"stream\""), "body must carry the 'stream' flag: " + body);
        assertTrue(body.contains("false"),
                "body must set stream=false: " + body);
    }

    @Test
    @DisplayName("generate parses the 'response' field as a String")
    void generate_parsesResponseText() throws IOException {
        stub.respondWith("/api/generate", 200, fixture("generate-response.json"));
        Optional<String> reply = client.generate("anything", "llama3.2");
        assertTrue(reply.isPresent());
        assertEquals("Paris is the capital of France.", reply.get());
    }

    @Test
    @DisplayName("generate returns empty Optional on HTTP 500")
    void generate_returnsEmptyOnHttp500() {
        stub.respondWith("/api/generate", 500, "{}");
        assertTrue(client.generate("anything", "llama3.2").isEmpty());
    }

    @Test
    @DisplayName("generate(..., contextDocuments) concatenates documents before the user prompt")
    void generateWithContext_concatenatesDocumentsBeforePrompt() throws IOException {
        stub.respondWith("/api/generate", 200, fixture("generate-response.json"));

        List<String> documents = List.of(
                "The capital of France is Paris.",
                "Paris sits on the river Seine in northern France.");
        String question = "Which river is the French capital located on?";

        client.generate(question, "llama3.2", documents);

        OllamaStubServer.RecordedRequest request = stub.lastRequestTo("/api/generate");
        String body = request.body();

        assertTrue(body.contains("The capital of France is Paris."),
                "first document must appear in the request body: " + body);
        assertTrue(body.contains("Paris sits on the river Seine in northern France."),
                "second document must appear in the request body: " + body);
        assertTrue(body.contains(question),
                "user question must appear in the request body: " + body);

        int firstDocumentIndex = body.indexOf("The capital of France is Paris.");
        int secondDocumentIndex = body.indexOf("Paris sits on the river Seine in northern France.");
        int questionIndex = body.indexOf(question);

        assertTrue(firstDocumentIndex < questionIndex,
                "documents must appear before the user question in the prompt");
        assertTrue(secondDocumentIndex < questionIndex,
                "every document must precede the user question");
        assertTrue(firstDocumentIndex < secondDocumentIndex,
                "documents should keep their input order");
    }
}
