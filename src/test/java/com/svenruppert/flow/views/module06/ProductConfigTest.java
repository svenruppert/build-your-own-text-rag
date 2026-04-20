package com.svenruppert.flow.views.module06;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterisation test for {@link ProductConfig}: every constant is
 * pinned to its shipped value so a casual edit surfaces as a visible
 * test failure rather than a silent behaviour change.
 *
 * <p>This is deliberately paranoid -- the whole point of module 6 is
 * that these defaults <em>are</em> the product decisions, and the
 * workshop's downstream slides quote several of them (chunk size 400,
 * retrieval k 5, RRF constant 60). A test here is the cheapest way to
 * keep slide and code in sync.
 */
class ProductConfigTest {

    @Test
    @DisplayName("embedding model is nomic-embed-text-v2-moe")
    void embeddingModel() {
        assertEquals("nomic-embed-text-v2-moe", ProductConfig.DEFAULT_EMBEDDING_MODEL);
    }

    @Test
    @DisplayName("generation model is gemma4:e4b")
    void generationModel() {
        assertEquals("gemma4:e4b", ProductConfig.DEFAULT_GENERATION_MODEL);
    }

    @Test
    @DisplayName("chunk target size is 400 characters")
    void chunkTargetSize() {
        assertEquals(400, ProductConfig.CHUNK_TARGET_SIZE);
    }

    @Test
    @DisplayName("retrieval k is 5")
    void retrievalK() {
        assertEquals(5, ProductConfig.RETRIEVAL_K);
    }

    @Test
    @DisplayName("hybrid first-stage k is 10")
    void hybridFirstStageK() {
        assertEquals(10, ProductConfig.HYBRID_FIRST_STAGE_K);
    }

    @Test
    @DisplayName("RRF constant is 60.0 (paper default)")
    void rrfK() {
        assertEquals(60.0, ProductConfig.RRF_K, 0.0);
    }

    @Test
    @DisplayName("grounding check is on by default")
    void groundingCheckDefault() {
        assertTrue(ProductConfig.GROUNDING_CHECK_DEFAULT);
    }

    @Test
    @DisplayName("reranking is off by default")
    void rerankingDefault() {
        assertFalse(ProductConfig.RERANKING_DEFAULT);
    }
}
