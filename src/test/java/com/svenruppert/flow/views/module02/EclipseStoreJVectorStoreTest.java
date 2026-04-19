package com.svenruppert.flow.views.module02;

import com.svenruppert.flow.views.module02.testutil.VectorFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("EclipseStoreJVectorStore contract + persistence")
class EclipseStoreJVectorStoreTest extends VectorStoreContractTest {

    private Path storageDirectory;

    @Override
    protected VectorStore createStore() throws IOException {
        storageDirectory = Files.createTempDirectory("jvector-store-test-");
        return new EclipseStoreJVectorStore(storageDirectory);
    }

    @Override
    protected void cleanupStore(VectorStore store) throws Exception {
        try {
            if (store != null) store.close();
        } finally {
            deleteRecursively(storageDirectory);
        }
    }

    /**
     * Beyond the shared contract: persist a corpus, close the store,
     * open a new store on the same directory and verify that the
     * vectors are back -- exactly the restart scenario a production
     * deployment would go through. Embedding is not re-run (there is
     * no LlmClient involved): the persisted raw vectors are the only
     * source of truth.
     */
    @Test
    @DisplayName("restart reloads persisted vectors without re-embedding")
    void restart_reloadsPersistedVectorsWithoutReembedding() throws Exception {
        // First store: populate and shut down.
        populateFromFixture();
        int originalSize = store.size();
        float[] query = {1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        List<SearchHit> beforeRestart = store.search(query, 3);
        store.close();

        // Second store on the same directory: should find the same
        // corpus without any re-embedding.
        try (EclipseStoreJVectorStore restarted =
                     new EclipseStoreJVectorStore(storageDirectory)) {
            assertEquals(originalSize, restarted.size(),
                    "persisted corpus size must survive a restart");

            List<SearchHit> afterRestart = restarted.search(query, 3);

            // Compare by id set to avoid over-asserting the internal
            // HNSW tie-breaking order; scores must be identical (we
            // recompute exact cosine ourselves in JVectorIndexSnapshot).
            assertEquals(idsOf(beforeRestart), idsOf(afterRestart));
            assertEquals(scoresSorted(beforeRestart), scoresSorted(afterRestart));
        }
    }

    // Helpers -------------------------------------------------------

    private static List<String> idsOf(List<SearchHit> hits) {
        return hits.stream().map(SearchHit::id).sorted().toList();
    }

    private static List<Double> scoresSorted(List<SearchHit> hits) {
        return hits.stream().map(SearchHit::score).sorted().toList();
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // robust: tolerate already-gone files
                }
            });
        }
    }

    // Sanity check ---------------------------------------------------

    @Test
    @DisplayName("fixture loader returns the 10 expected ids")
    void fixture_is_wellFormed() {
        List<RawVectorEntry> entries = VectorFixtures.load();
        assertEquals(10, entries.size());
        assertTrue(entries.stream().allMatch(e -> e.vector().length == 8));
        assertTrue(entries.stream().anyMatch(e -> e.id().equals("v-orthogonal")));
    }
}
