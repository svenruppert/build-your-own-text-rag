package com.svenruppert.flow.views.module04;

import com.svenruppert.flow.views.module02.DefaultSimilarity;
import com.svenruppert.flow.views.module02.InMemoryVectorStore;
import com.svenruppert.flow.views.module02.VectorStore;
import com.svenruppert.flow.views.module03.Chunk;
import com.svenruppert.flow.views.module04.testutil.StubLlmClient;
import com.svenruppert.flow.views.module04.testutil.TestChunks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorRetrieverTest {

    private static final String MODEL = "stub-model";

    @Test
    @DisplayName("returns hits in descending score order")
    void retrieve_returnsHitsInDescendingScoreOrder() {
        VectorStore store = new InMemoryVectorStore(new DefaultSimilarity());
        Map<String, Chunk> registry = new HashMap<>();
        Chunk a = TestChunks.of(0, "apple");
        Chunk b = TestChunks.of(1, "banana");
        Chunk c = TestChunks.of(2, "cherry");
        store.add("0", new float[]{1f, 0f, 0f}, a.text());
        store.add("1", new float[]{0f, 1f, 0f}, b.text());
        store.add("2", new float[]{0.5f, 0.5f, 0f}, c.text());
        registry.put("0", a);
        registry.put("1", b);
        registry.put("2", c);

        StubLlmClient llm = new StubLlmClient()
                .withEmbed("apple", new float[]{1f, 0f, 0f});

        List<RetrievalHit> hits = new VectorRetriever(llm, MODEL, store, registry)
                .retrieve("apple", 3);

        assertEquals(3, hits.size());
        assertTrue(hits.get(0).score() >= hits.get(1).score());
        assertTrue(hits.get(1).score() >= hits.get(2).score());
        assertEquals("apple", hits.get(0).chunk().text());
    }

    @Test
    @DisplayName("returns empty list when embedding fails")
    void retrieve_returnsEmptyListWhenEmbeddingFails() {
        VectorStore store = new InMemoryVectorStore(new DefaultSimilarity());
        Map<String, Chunk> registry = new HashMap<>();
        store.add("0", new float[]{1f, 0f, 0f}, "x");
        registry.put("0", TestChunks.of(0, "x"));

        // No canned embedding for the query -> StubLlmClient returns empty.
        StubLlmClient llm = new StubLlmClient();

        List<RetrievalHit> hits = new VectorRetriever(llm, MODEL, store, registry)
                .retrieve("nothing to embed", 3);

        assertTrue(hits.isEmpty());
    }

    @Test
    @DisplayName("joins each hit id back to its chunk in the registry")
    void retrieve_joinsChunkRegistryCorrectly() {
        VectorStore store = new InMemoryVectorStore(new DefaultSimilarity());
        Map<String, Chunk> registry = new HashMap<>();
        Chunk a = TestChunks.of(0, "alpha");
        Chunk b = TestChunks.of(1, "beta");
        store.add("alpha-id", new float[]{1f, 0f}, a.text());
        store.add("beta-id", new float[]{0f, 1f}, b.text());
        registry.put("alpha-id", a);
        registry.put("beta-id", b);

        StubLlmClient llm = new StubLlmClient()
                .withEmbed("query", new float[]{1f, 0f});

        List<RetrievalHit> hits = new VectorRetriever(llm, MODEL, store, registry)
                .retrieve("query", 2);

        assertEquals(2, hits.size());
        assertEquals(a, hits.get(0).chunk());
        assertEquals("vector", hits.get(0).sourceLabel());
    }
}
