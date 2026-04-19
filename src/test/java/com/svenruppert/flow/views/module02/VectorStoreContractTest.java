package com.svenruppert.flow.views.module02;

import com.svenruppert.flow.views.module02.testutil.VectorFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared behavioural contract for every {@link VectorStore} in the
 * workshop. Concrete subclasses provide a factory for the store under
 * test plus any cleanup logic they need.
 *
 * <p>All assertions work on the deterministic fixture in
 * {@code /module02/fixtures/vectors.json}: the values are designed so
 * that the intended top-k hits fall out of cosine similarity on paper.
 */
abstract class VectorStoreContractTest {

    private static final double SCORE_TOLERANCE = 1.0e-5;

    /** Fresh store per test. Subclasses decide how to build it. */
    protected abstract VectorStore createStore() throws Exception;

    /** Clean up after a test. Default: call {@link VectorStore#close()}. */
    protected void cleanupStore(VectorStore store) throws Exception {
        if (store != null) store.close();
    }

    protected VectorStore store;

    @BeforeEach
    void setUp() throws Exception {
        store = createStore();
    }

    @AfterEach
    void tearDown() throws Exception {
        cleanupStore(store);
    }

    /** Loads the fixture into {@code store} (all 10 entries). */
    protected void populateFromFixture() {
        for (RawVectorEntry entry : VectorFixtures.load()) {
            store.add(entry.id(), entry.vector(), entry.payload());
        }
    }

    // ---------------------------------------------------------------

    @Test
    @DisplayName("add then search returns an exact match with score 1.0")
    void addAndSearch_returnsExactMatch() {
        RawVectorEntry anchor = VectorFixtures.byId("v-cluster-a-0");
        store.add(anchor.id(), anchor.vector(), anchor.payload());

        List<SearchHit> hits = store.search(anchor.vector(), 1);

        assertEquals(1, hits.size());
        assertEquals(anchor.id(), hits.get(0).id());
        assertEquals(anchor.payload(), hits.get(0).payload());
        assertEquals(1.0, hits.get(0).score(), SCORE_TOLERANCE);
    }

    @Test
    @DisplayName("search returns top-k sorted by descending score")
    void addAndSearch_returnsTopKSortedByDescendingScore() {
        populateFromFixture();

        float[] queryNearE0 = {1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        List<SearchHit> hits = store.search(queryNearE0, 3);

        assertEquals(3, hits.size());
        // Cluster A is near e_0 by construction; expect the three
        // cluster-A ids to be the top three, in some score-descending
        // order (not locking the inter-cluster-A ordering because
        // approximate search may reorder ties slightly).
        Set<String> expected = Set.of("v-cluster-a-0", "v-cluster-a-1", "v-cluster-a-2");
        Set<String> actual = Set.of(
                hits.get(0).id(), hits.get(1).id(), hits.get(2).id());
        assertEquals(expected, actual);
        // Strictly non-increasing scores.
        assertTrue(hits.get(0).score() >= hits.get(1).score());
        assertTrue(hits.get(1).score() >= hits.get(2).score());
    }

    @Test
    @DisplayName("search with k > size returns every entry")
    void search_withKLargerThanSize_returnsAllEntries() {
        populateFromFixture();
        int all = store.size();
        assertEquals(10, all, "fixture carries 10 entries");

        float[] anyQuery = {1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        List<SearchHit> hits = store.search(anyQuery, 100);

        assertEquals(all, hits.size());
    }

    @Test
    @DisplayName("add with an existing id overwrites the previous entry")
    void add_duplicateId_overwritesPreviousEntry() {
        float[] original = {1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        float[] replacement = {0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};

        store.add("dup", original, "original");
        assertEquals(1, store.size());

        store.add("dup", replacement, "replaced");
        assertEquals(1, store.size(), "duplicate id must not grow the store");

        // Query along the replacement direction: the stored vector
        // should now match perfectly, which would not happen if the
        // original was still in place.
        List<SearchHit> hits = store.search(replacement, 1);
        assertEquals(1, hits.size());
        assertEquals("dup", hits.get(0).id());
        assertEquals("replaced", hits.get(0).payload());
        assertEquals(1.0, hits.get(0).score(), SCORE_TOLERANCE);
    }

    @Test
    @DisplayName("size reflects the number of distinct ids")
    void size_reflectsNumberOfDistinctIds() {
        float[] v = {1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        store.add("a", v, "alpha");
        store.add("b", v, "beta");
        store.add("c", v, "gamma");
        assertEquals(3, store.size());

        // Re-adding 'a' with a different payload must not change size.
        store.add("a", v, "alpha-2");
        assertEquals(3, store.size());
    }

    @Test
    @DisplayName("clear removes every entry")
    void clear_removesAllEntries() {
        populateFromFixture();
        assertEquals(10, store.size());

        store.clear();

        assertEquals(0, store.size());
        assertTrue(store.search(new float[8], 5).isEmpty());
    }

    @Test
    @DisplayName("search on an empty store returns an empty list")
    void search_onEmptyStore_returnsEmptyList() {
        assertEquals(0, store.size());
        List<SearchHit> hits = store.search(new float[8], 5);
        assertTrue(hits.isEmpty());
    }
}
