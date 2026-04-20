package com.svenruppert.flow.views.module04;

import com.svenruppert.flow.views.module03.Chunk;
import com.svenruppert.flow.views.module04.testutil.TestChunks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BM25RetrieverTest {

    private LuceneBM25KeywordIndex index;
    private final Map<String, Chunk> registry = new HashMap<>();

    @BeforeEach
    void setUp() throws IOException {
        index = new LuceneBM25KeywordIndex();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (index != null) index.close();
    }

    private void addChunk(int i, String text) throws IOException {
        String id = "id-" + i;
        Chunk chunk = TestChunks.of(i, text);
        index.add(id, text);
        registry.put(id, chunk);
    }

    @Test
    @DisplayName("matches a document whose term is present")
    void retrieve_matchesTermPresent() throws IOException {
        addChunk(0, "The quick brown fox jumps over the lazy dog.");
        addChunk(1, "A completely unrelated paragraph about cooking recipes.");
        addChunk(2, "Algorithms and data structures for sorting arrays.");

        List<RetrievalHit> hits = new BM25Retriever(index, registry).retrieve("fox", 5);

        assertEquals(1, hits.size());
        assertEquals("id-0", findIdFor(hits.get(0).chunk()));
        assertEquals("bm25", hits.get(0).sourceLabel());
    }

    @Test
    @DisplayName("ranks a rare term ahead of a common one (IDF)")
    void retrieve_ranksBySpecificityRareOverCommon() throws IOException {
        // All three contain the common term "the" but only id-1 contains "photosynthesis".
        addChunk(0, "The cat sat on the mat the whole day.");
        addChunk(1, "Photosynthesis converts light into chemical energy.");
        addChunk(2, "The sun was warm and the breeze was gentle.");

        List<RetrievalHit> hits = new BM25Retriever(index, registry)
                .retrieve("photosynthesis", 3);

        assertEquals(1, hits.size());
        assertEquals("id-1", findIdFor(hits.get(0).chunk()));
    }

    @Test
    @DisplayName("returns an empty list against an empty index")
    void retrieve_returnsEmptyListForEmptyIndex() {
        List<RetrievalHit> hits = new BM25Retriever(index, registry).retrieve("anything", 5);
        assertTrue(hits.isEmpty());
    }

    private String findIdFor(Chunk chunk) {
        return registry.entrySet().stream()
                .filter(e -> e.getValue().equals(chunk))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("sanity: LuceneBM25KeywordIndex accepts documents")
    void sanity_indexAcceptsDocuments() throws IOException {
        addChunk(0, "one document");
        addChunk(1, "two documents");
        assertEquals(2, index.size());
        assertNotNull(registry.get("id-0"));
    }
}
