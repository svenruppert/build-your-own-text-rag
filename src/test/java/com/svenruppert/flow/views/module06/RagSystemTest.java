package com.svenruppert.flow.views.module06;

import com.svenruppert.flow.views.module02.DefaultSimilarity;
import com.svenruppert.flow.views.module02.InMemoryVectorStore;
import com.svenruppert.flow.views.module03.SentenceChunker;
import com.svenruppert.flow.views.module04.testutil.StubLlmClient;
import com.svenruppert.flow.views.module05.GeneratedAnswer;
import com.svenruppert.flow.views.module05.SimpleGroundedPromptTemplate;
import com.svenruppert.flow.views.module05.testutil.StubStreamingLlmApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test of {@link RagSystem}: build with stubs, ingest two
 * short documents, ask a question, assert that the answer text was
 * assembled from the stream and that the two documents registered as
 * such in the counters.
 *
 * <p>Grounding and reranking are disabled so the stubs stay small:
 * grounding would require a second {@code LlmClient.generate} round,
 * and reranking would require a per-candidate generate response. Both
 * are covered separately in their own module tests.
 */
class RagSystemTest {

    private static final String DOC_ONE = "Alpha content lives here.";
    private static final String DOC_TWO = "Beta content lives here.";

    @Test
    @DisplayName("ingest two documents, ask a question, stream the answer")
    void ingestAskAnswer() {
        StubLlmClient llm = new StubLlmClient()
                // Query embedding -- VectorRetriever will embed the query
                // with this model before cosine-searching the store.
                .withEmbed("what is here?", new float[]{1f, 0f, 0f})
                .withEmbed(DOC_ONE, new float[]{1f, 0f, 0f})
                .withEmbed(DOC_TWO, new float[]{0f, 1f, 0f});

        StubStreamingLlmApi streaming = new StubStreamingLlmApi()
                .emitting("Al", "pha ", "is ", "the ", "answer.");

        try (RagSystem system = RagSystem.builder()
                .llmClient(llm)
                .vectorStore(new InMemoryVectorStore(new DefaultSimilarity()))
                .chunker(new SentenceChunker(400))
                .promptTemplate(new SimpleGroundedPromptTemplate())
                .streamingApi(streaming)
                .withGroundingCheck(false)
                .withReranking(false)
                .build()) {

            IngestionResult first = system.ingest("alpha.txt", DOC_ONE);
            IngestionResult second = system.ingest("beta.txt", DOC_TWO);

            assertEquals(1, first.chunkCount(), "alpha.txt should produce one chunk");
            assertEquals(1, second.chunkCount(), "beta.txt should produce one chunk");
            assertEquals(2, system.documentCount());
            assertEquals(2, system.chunkCount());

            List<String> streamed = new ArrayList<>();
            GeneratedAnswer answer = system.ask("what is here?", streamed::add);

            assertFalse(answer.text().isBlank(), "answer text should be non-empty");
            assertEquals("Alpha is the answer.", answer.text());
            assertFalse(streamed.isEmpty(), "tokenSink should have received tokens");
            assertTrue(answer.groundingCheck().isEmpty(),
                    "grounding disabled -> no check attached");
        } catch (Exception e) {
            throw new AssertionError("RagSystem.close failed: " + e.getMessage(), e);
        }
    }

    @Test
    @DisplayName("listSources() returns ingested filenames in ingestion order")
    void listSourcesPreservesOrder() {
        try (RagSystem system = RagSystem.builder()
                .llmClient(new StubLlmClient()
                        .withEmbed(DOC_ONE, new float[]{1f, 0f, 0f})
                        .withEmbed(DOC_TWO, new float[]{0f, 1f, 0f}))
                .vectorStore(new InMemoryVectorStore(new DefaultSimilarity()))
                .chunker(new SentenceChunker(400))
                .promptTemplate(new SimpleGroundedPromptTemplate())
                .streamingApi(new StubStreamingLlmApi())
                .withGroundingCheck(false)
                .build()) {

            system.ingest("alpha.txt", DOC_ONE);
            system.ingest("beta.txt", DOC_TWO);

            assertIterableEquals(List.of("alpha.txt", "beta.txt"), system.listSources());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("removeSource(name) drops exactly that document's chunks")
    void removeSourceDropsOnlyThatDocument() {
        try (RagSystem system = RagSystem.builder()
                .llmClient(new StubLlmClient()
                        .withEmbed(DOC_ONE, new float[]{1f, 0f, 0f})
                        .withEmbed(DOC_TWO, new float[]{0f, 1f, 0f}))
                .vectorStore(new InMemoryVectorStore(new DefaultSimilarity()))
                .chunker(new SentenceChunker(400))
                .promptTemplate(new SimpleGroundedPromptTemplate())
                .streamingApi(new StubStreamingLlmApi())
                .withGroundingCheck(false)
                .build()) {

            system.ingest("alpha.txt", DOC_ONE);
            system.ingest("beta.txt", DOC_TWO);
            assertEquals(2, system.documentCount());

            int removed = system.removeSource("alpha.txt");
            assertEquals(1, removed);
            assertEquals(1, system.documentCount());
            assertEquals(1, system.chunkCount());
            assertIterableEquals(List.of("beta.txt"), system.listSources());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("removeSource() on an unknown name is a silent no-op")
    void removeSourceOnUnknownNameIsNoop() {
        try (RagSystem system = RagSystem.builder()
                .llmClient(new StubLlmClient()
                        .withEmbed(DOC_ONE, new float[]{1f, 0f, 0f}))
                .vectorStore(new InMemoryVectorStore(new DefaultSimilarity()))
                .chunker(new SentenceChunker(400))
                .promptTemplate(new SimpleGroundedPromptTemplate())
                .streamingApi(new StubStreamingLlmApi())
                .withGroundingCheck(false)
                .build()) {

            system.ingest("alpha.txt", DOC_ONE);

            assertEquals(0, system.removeSource("ghost.txt"));
            assertEquals(1, system.documentCount());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("clearAll() wipes every source and leaves the system reusable")
    void clearAllWipesEverythingAndReuseWorks() {
        StubLlmClient llm = new StubLlmClient()
                .withEmbed(DOC_ONE, new float[]{1f, 0f, 0f})
                .withEmbed(DOC_TWO, new float[]{0f, 1f, 0f});
        try (RagSystem system = RagSystem.builder()
                .llmClient(llm)
                .vectorStore(new InMemoryVectorStore(new DefaultSimilarity()))
                .chunker(new SentenceChunker(400))
                .promptTemplate(new SimpleGroundedPromptTemplate())
                .streamingApi(new StubStreamingLlmApi())
                .withGroundingCheck(false)
                .build()) {

            system.ingest("alpha.txt", DOC_ONE);
            system.ingest("beta.txt", DOC_TWO);

            system.clearAll();

            assertEquals(0, system.documentCount());
            assertEquals(0, system.chunkCount());
            assertTrue(system.listSources().isEmpty());

            // System is still usable -- re-ingesting works after a wipe.
            system.ingest("alpha.txt", DOC_ONE);
            assertEquals(1, system.documentCount());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
