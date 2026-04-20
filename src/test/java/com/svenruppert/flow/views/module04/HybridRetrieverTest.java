package com.svenruppert.flow.views.module04;

import com.svenruppert.flow.views.module03.Chunk;
import com.svenruppert.flow.views.module04.testutil.TestChunks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridRetrieverTest {

    // Shared chunks used in each test.
    private final Chunk A = TestChunks.of(0, "chunk A");
    private final Chunk B = TestChunks.of(1, "chunk B");
    private final Chunk C = TestChunks.of(2, "chunk C");
    private final Chunk D = TestChunks.of(3, "chunk D");

    /** Constant-return retriever: emits a canned list regardless of query/k. */
    private static Retriever canned(List<RetrievalHit> hits) {
        return (q, k) -> hits.stream().limit(k).toList();
    }

    private static RetrievalHit hit(Chunk chunk, double score, String label) {
        return new RetrievalHit(chunk, score, label);
    }

    @Test
    @DisplayName("RRF combines ranks without caring about score scale")
    void rrf_combinesRanksWithoutCaringAboutScoreScale() {
        // Vector ranks: A > B > C
        // BM25   ranks: D > A > C
        // Under RRF, A appears in both top lists and should rank first.
        Retriever vectorHits = canned(List.of(
                hit(A, 0.99, "vector"),
                hit(B, 0.80, "vector"),
                hit(C, 0.50, "vector")));
        Retriever bm25Hits = canned(List.of(
                hit(D, 42.7, "bm25"),   // BM25 scale unrelated to cosine
                hit(A, 18.1, "bm25"),
                hit(C, 2.0, "bm25")));

        HybridRetriever hybrid = new HybridRetriever(
                vectorHits, bm25Hits,
                new FusionStrategy.ReciprocalRankFusion(60.0),
                10);

        List<RetrievalHit> result = hybrid.retrieve("anything", 3);
        assertEquals(A, result.get(0).chunk(),
                "A appears in both ranked lists and should win RRF");
        assertTrue(extractChunks(result).containsAll(List.of(A)));
        for (RetrievalHit r : result) assertEquals("hybrid", r.sourceLabel());
    }

    @Test
    @DisplayName("weighted fusion with (1, 0) collapses to the vector path")
    void weighted_combinesScoresAccordingToWeights_vectorOnly() {
        Retriever vectorHits = canned(List.of(
                hit(A, 0.9, "vector"),
                hit(B, 0.6, "vector")));
        Retriever bm25Hits = canned(List.of(
                hit(C, 5.0, "bm25"),
                hit(D, 3.0, "bm25")));

        HybridRetriever hybrid = new HybridRetriever(
                vectorHits, bm25Hits,
                new FusionStrategy.WeightedScoreFusion(1.0, 0.0),
                10);

        List<RetrievalHit> result = hybrid.retrieve("q", 2);
        assertEquals(List.of(A, B), extractChunks(result));
    }

    @Test
    @DisplayName("weighted fusion with (0, 1) collapses to the BM25 path")
    void weighted_combinesScoresAccordingToWeights_bm25Only() {
        Retriever vectorHits = canned(List.of(
                hit(A, 0.9, "vector"),
                hit(B, 0.6, "vector")));
        Retriever bm25Hits = canned(List.of(
                hit(C, 5.0, "bm25"),
                hit(D, 3.0, "bm25")));

        HybridRetriever hybrid = new HybridRetriever(
                vectorHits, bm25Hits,
                new FusionStrategy.WeightedScoreFusion(0.0, 1.0),
                10);

        List<RetrievalHit> result = hybrid.retrieve("q", 2);
        assertEquals(List.of(C, D), extractChunks(result));
    }

    @Test
    @DisplayName("weighted fusion with (0.5, 0.5) balances both normalised contributions")
    void weighted_combinesScoresAccordingToWeights_balanced() {
        // A is vector-unique (norm 1.0) and BM25-missing (0.0) -> 0.5.
        // B is BM25-unique (norm 1.0) and vector-missing     -> 0.5.
        // C is in both: vector 0.5 normalised -> 0.5 * 0.5 = 0.25
        //               bm25  2.5 normalised to 0.5 -> 0.5 * 0.5 = 0.25
        //              combined 0.5.
        Retriever vectorHits = canned(List.of(
                hit(A, 1.0, "vector"),
                hit(C, 0.5, "vector")));
        Retriever bm25Hits = canned(List.of(
                hit(B, 5.0, "bm25"),
                hit(C, 2.5, "bm25")));

        HybridRetriever hybrid = new HybridRetriever(
                vectorHits, bm25Hits,
                new FusionStrategy.WeightedScoreFusion(0.5, 0.5),
                10);

        List<RetrievalHit> result = hybrid.retrieve("q", 3);
        // All three appear, all three have the same fused score (0.5);
        // we only assert on set membership to stay stable against tie-break order.
        assertEquals(3, result.size());
        List<Chunk> chunks = extractChunks(result);
        assertTrue(chunks.containsAll(List.of(A, B, C)));
        for (RetrievalHit r : result) {
            assertEquals(0.5, r.score(), 1.0e-9);
        }
    }

    @Test
    @DisplayName("retrieve limits the result to top k")
    void search_limitsToTopK() {
        List<RetrievalHit> vectorHits = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            vectorHits.add(hit(TestChunks.of(i, "v" + i), 1.0 - 0.1 * i, "vector"));
        }
        List<RetrievalHit> bm25Hits = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            bm25Hits.add(hit(TestChunks.of(10 + i, "b" + i), 5.0 - i, "bm25"));
        }

        HybridRetriever hybrid = new HybridRetriever(
                canned(vectorHits), canned(bm25Hits),
                new FusionStrategy.ReciprocalRankFusion(60.0),
                10);

        assertEquals(3, hybrid.retrieve("q", 3).size());
        assertEquals(1, hybrid.retrieve("q", 1).size());
    }

    private static List<Chunk> extractChunks(List<RetrievalHit> hits) {
        return hits.stream().map(RetrievalHit::chunk).toList();
    }
}
