package com.svenruppert.flow.views.module06;

import com.svenruppert.flow.views.module02.DefaultSimilarity;
import com.svenruppert.flow.views.module02.InMemoryVectorStore;
import com.svenruppert.flow.views.module03.SentenceChunker;
import com.svenruppert.flow.views.module04.FusionStrategy;
import com.svenruppert.flow.views.module04.testutil.StubLlmClient;
import com.svenruppert.flow.views.module05.SimpleGroundedPromptTemplate;
import com.svenruppert.flow.views.module05.testutil.StubStreamingLlmApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the validation and wiring logic of {@link RagSystemBuilder}.
 *
 * <p>The builder's job is twofold: refuse to produce a partially wired
 * system (each required field missing must surface as a named entry in
 * the exception message) and apply the toggles in the right places
 * (reranking wraps the base retriever; grounding check attaches to the
 * pipeline). The retriever-mode cases are a smoke test -- since
 * {@link RagSystem} does not expose the retriever, we verify that
 * {@code build()} succeeds for each mode and that downstream state is
 * usable.
 */
class RagSystemBuilderTest {

    private static RagSystemBuilder filled() {
        return RagSystem.builder()
                .llmClient(new StubLlmClient())
                .vectorStore(new InMemoryVectorStore(new DefaultSimilarity()))
                .chunker(new SentenceChunker(400))
                .promptTemplate(new SimpleGroundedPromptTemplate())
                .streamingApi(new StubStreamingLlmApi());
    }

    @Test
    @DisplayName("happy path: all required fields set -> build() returns a system")
    void happyPath() {
        RagSystem system = filled().build();
        assertNotNull(system);
        assertEquals(0, system.chunkCount());
        assertEquals(0, system.documentCount());
    }

    @Test
    @DisplayName("missing llmClient is named in the exception")
    void missingLlmClient() {
        RagSystemBuilder builder = RagSystem.builder()
                .vectorStore(new InMemoryVectorStore(new DefaultSimilarity()))
                .chunker(new SentenceChunker(400))
                .promptTemplate(new SimpleGroundedPromptTemplate())
                .streamingApi(new StubStreamingLlmApi());
        IllegalStateException e = assertThrows(IllegalStateException.class, builder::build);
        assertTrue(e.getMessage().contains("llmClient"),
                "expected message to name 'llmClient', got: " + e.getMessage());
    }

    @Test
    @DisplayName("missing vectorStore is named in the exception")
    void missingVectorStore() {
        RagSystemBuilder builder = RagSystem.builder()
                .llmClient(new StubLlmClient())
                .chunker(new SentenceChunker(400))
                .promptTemplate(new SimpleGroundedPromptTemplate())
                .streamingApi(new StubStreamingLlmApi());
        IllegalStateException e = assertThrows(IllegalStateException.class, builder::build);
        assertTrue(e.getMessage().contains("vectorStore"),
                "expected message to name 'vectorStore', got: " + e.getMessage());
    }

    @Test
    @DisplayName("missing chunker is named in the exception")
    void missingChunker() {
        RagSystemBuilder builder = RagSystem.builder()
                .llmClient(new StubLlmClient())
                .vectorStore(new InMemoryVectorStore(new DefaultSimilarity()))
                .promptTemplate(new SimpleGroundedPromptTemplate())
                .streamingApi(new StubStreamingLlmApi());
        IllegalStateException e = assertThrows(IllegalStateException.class, builder::build);
        assertTrue(e.getMessage().contains("chunker"),
                "expected message to name 'chunker', got: " + e.getMessage());
    }

    @Test
    @DisplayName("missing promptTemplate is named in the exception")
    void missingPromptTemplate() {
        RagSystemBuilder builder = RagSystem.builder()
                .llmClient(new StubLlmClient())
                .vectorStore(new InMemoryVectorStore(new DefaultSimilarity()))
                .chunker(new SentenceChunker(400))
                .streamingApi(new StubStreamingLlmApi());
        IllegalStateException e = assertThrows(IllegalStateException.class, builder::build);
        assertTrue(e.getMessage().contains("promptTemplate"),
                "expected message to name 'promptTemplate', got: " + e.getMessage());
    }

    @Test
    @DisplayName("missing streamingApi is named in the exception")
    void missingStreamingApi() {
        RagSystemBuilder builder = RagSystem.builder()
                .llmClient(new StubLlmClient())
                .vectorStore(new InMemoryVectorStore(new DefaultSimilarity()))
                .chunker(new SentenceChunker(400))
                .promptTemplate(new SimpleGroundedPromptTemplate());
        IllegalStateException e = assertThrows(IllegalStateException.class, builder::build);
        assertTrue(e.getMessage().contains("streamingApi"),
                "expected message to name 'streamingApi', got: " + e.getMessage());
    }

    @Test
    @DisplayName("rerankingEnabled=true still builds a valid system")
    void rerankingEnabledBuilds() {
        RagSystem system = filled().withReranking(true).build();
        assertNotNull(system);
    }

    @Test
    @DisplayName("groundingCheckEnabled=true still builds a valid system")
    void groundingCheckEnabledBuilds() {
        RagSystem system = filled().withGroundingCheck(true).build();
        assertNotNull(system);
    }

    @Test
    @DisplayName("retrieverMode=VECTOR_ONLY builds a valid system")
    void retrieverModeVectorOnly() {
        assertDoesNotThrow(() -> filled()
                .retrieverMode(RagSystemBuilder.RetrieverMode.VECTOR_ONLY)
                .build());
    }

    @Test
    @DisplayName("retrieverMode=BM25_ONLY builds a valid system")
    void retrieverModeBm25Only() {
        assertDoesNotThrow(() -> filled()
                .retrieverMode(RagSystemBuilder.RetrieverMode.BM25_ONLY)
                .build());
    }

    @Test
    @DisplayName("retrieverMode=HYBRID with custom fusion strategy builds")
    void retrieverModeHybrid() {
        assertDoesNotThrow(() -> filled()
                .retrieverMode(RagSystemBuilder.RetrieverMode.HYBRID)
                .fusionStrategy(new FusionStrategy.ReciprocalRankFusion(42.0))
                .hybridFirstStageK(8)
                .build());
    }
}
