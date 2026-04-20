package com.svenruppert.flow.views.module02;

import com.svenruppert.dependencies.core.logger.HasLogger;
import io.github.jbellis.jvector.graph.GraphIndexBuilder;
import io.github.jbellis.jvector.graph.GraphSearcher;
import io.github.jbellis.jvector.graph.ImmutableGraphIndex;
import io.github.jbellis.jvector.graph.RandomAccessVectorValues;
import io.github.jbellis.jvector.graph.SearchResult;
import io.github.jbellis.jvector.util.Bits;
import io.github.jbellis.jvector.vector.VectorSimilarityFunction;
import io.github.jbellis.jvector.vector.VectorizationProvider;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import io.github.jbellis.jvector.vector.types.VectorTypeSupport;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Industrial-scale {@link VectorStore}: EclipseStore persists the raw
 * embedding vectors on disk, and JVector maintains an in-memory HNSW
 * index for fast approximate nearest-neighbour search.
 *
 * <h2>Two responsibilities</h2>
 * <ul>
 *   <li><strong>EclipseStore</strong> is the durable storage: the raw
 *       {@code float[]} vectors survive restarts. It is the only source
 *       of truth.</li>
 *   <li><strong>JVector</strong> is the acceleration layer: an HNSW graph
 *       that makes top-{@code k} cosine search sub-linear. It is
 *       deliberately <em>not</em> persisted -- on start-up the whole
 *       index is rebuilt in memory from the persisted vectors, which
 *       typically takes seconds for corpora the size we see in the
 *       workshop benchmarks.</li>
 * </ul>
 *
 * <p>The point of this split -- mirrored in every production vector
 * store -- is that embedding cost is the expensive thing. Rebuilding an
 * HNSW graph from 50 000 persisted float[]s costs a CPU-bound second or
 * two. Re-embedding the same 50 000 texts against Ollama would cost
 * many minutes and has nothing to do with correctness. The store
 * therefore saves only the vectors.
 *
 * <h2>Rebuild strategy</h2>
 * <p>Every {@link #add} and {@link #clear} marks the index dirty. The
 * rebuild itself runs lazily on the next {@link #search} call, under
 * an internal lock, and produces a fresh immutable snapshot that is
 * then published atomically via a {@code volatile} field.
 *
 * <p>This keeps the contract identical to "rebuild after every add"
 * (every search reflects every prior add) while letting the benchmark
 * ingest 50k+ vectors in seconds instead of rebuilding the HNSW graph
 * 50k times. Warmup queries in the benchmark absorb the one-shot
 * rebuild cost before timed queries begin.
 *
 * <h2>Thread-safety</h2>
 * <ul>
 *   <li>{@link #add}, {@link #clear} and the rebuild are serialised
 *       behind the implicit {@code synchronized} on this instance.</li>
 *   <li>{@link #search} reads the current index via a volatile field
 *       ({@link #currentSnapshot}); an in-flight search keeps running
 *       against the snapshot it observed at the start of the call,
 *       even if a concurrent add has already invalidated the dirty
 *       flag. That is the lock-free reader pattern the spec asks for.</li>
 * </ul>
 */
public final class EclipseStoreJVectorStore implements VectorStore, HasLogger {

    // HNSW parameters -- kept identical to the reference project.
    private static final int HNSW_M = 16;
    private static final int HNSW_EF_CONSTRUCTION = 100;
    private static final float HNSW_NEIGHBOR_OVERFLOW = 1.2f;
    private static final float HNSW_ALPHA = 1.2f;
    private static final boolean HNSW_ADD_HIERARCHY = true;

    private static final VectorSimilarityFunction SIMILARITY_FUNCTION =
            VectorSimilarityFunction.COSINE;

    private static final VectorTypeSupport VECTOR_TYPE_SUPPORT =
            VectorizationProvider.getInstance().getVectorTypeSupport();

    private static final Similarity COSINE = new DefaultSimilarity();

    private final Path storageDirectory;
    private final EmbeddedStorageManager storageManager;
    private final VectorStoreRoot root;

    /**
     * Current HNSW snapshot. Reads are lock-free: a rebuild publishes a
     * new snapshot, but a {@link #search} that already started keeps
     * operating on the one it observed and sees a consistent view.
     */
    private volatile JVectorIndexSnapshot currentSnapshot;

    /**
     * True when a mutation has landed since the last rebuild. Set under
     * the instance lock by mutators, cleared under the lock by the
     * rebuild, read lock-free by {@link #search}.
     */
    private volatile boolean indexDirty = true;

    /**
     * Opens (or creates, on first call) a persistent store rooted at the
     * given directory. On a fresh directory the HNSW index is empty; on
     * a directory that already holds persisted vectors the index is
     * rebuilt in memory from them on the first {@link #search}.
     */
    public EclipseStoreJVectorStore(Path storageDirectory) {
        this.storageDirectory = Objects.requireNonNull(storageDirectory, "storageDirectory");

        VectorStoreRoot seed = new VectorStoreRoot();
        this.storageManager = EmbeddedStorage.start(seed, storageDirectory);

        // If the directory already holds a persisted root, `manager.root()`
        // returns that one; otherwise we get our seed back.
        Object persistedRoot = storageManager.root();
        if (persistedRoot instanceof VectorStoreRoot vsr && vsr != seed) {
            // Loaded from disk: no re-persist needed.
            this.root = vsr;
        } else {
            // Fresh directory (or incompatible root): install seed and
            // persist it so a later restart finds a valid object graph.
            this.root = seed;
            storageManager.setRoot(this.root);
            storageManager.storeRoot();
        }

        initialise();
    }

    /**
     * Prepares the initial state. Defers the actual HNSW build to the
     * first {@link #search} call.
     */
    private void initialise() {
        currentSnapshot = JVectorIndexSnapshot.empty();
        indexDirty = !root.entries().isEmpty();
        logger().info("EclipseStoreJVectorStore initialised from {} with {} vectors",
                storageDirectory, root.entries().size());
    }

    @Override
    public synchronized void add(String id, float[] vector, String payload) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(vector, "vector");
        Objects.requireNonNull(payload, "payload");
        if (vector.length == 0) {
            throw new IllegalArgumentException("vector must not be empty");
        }
        // Defensive copy: callers may reuse float[] buffers, and we hand
        // the reference directly to the persistent root.
        float[] copy = vector.clone();
        root.entries().put(id, new RawVectorEntry(id, copy, payload));

        // Persist the map -- EclipseStore walks the object graph from it.
        storageManager.store(root.entries());

        indexDirty = true;
    }

    @Override
    public List<SearchHit> search(float[] queryVector, int k) {
        Objects.requireNonNull(queryVector, "queryVector");
        if (k <= 0) return List.of();
        JVectorIndexSnapshot snapshot = ensureSnapshot();
        if (snapshot.size() == 0) return List.of();
        return snapshot.search(queryVector, k);
    }

    @Override
    public int size() {
        return root.entries().size();
    }

    @Override
    public synchronized void clear() {
        root.entries().clear();
        storageManager.store(root.entries());
        currentSnapshot = JVectorIndexSnapshot.empty();
        indexDirty = false;
    }

    @Override
    public synchronized void remove(String id) {
        Objects.requireNonNull(id, "id");
        RawVectorEntry previous = root.entries().remove(id);
        if (previous == null) return;
        storageManager.store(root.entries());
        // HNSW graph cannot remove a node cheaply; mark dirty so the
        // next search rebuilds the snapshot from the surviving entries.
        indexDirty = true;
    }

    @Override
    public synchronized void close() {
        if (storageManager.isActive()) {
            storageManager.shutdown();
        }
        currentSnapshot = null;
    }

    /**
     * Double-checked rebuild: fast path when the index is clean, full
     * rebuild under the instance lock when the dirty flag is set. Runs
     * synchronously with any {@link #add} or {@link #clear} in progress.
     */
    private JVectorIndexSnapshot ensureSnapshot() {
        JVectorIndexSnapshot snapshot = currentSnapshot;
        if (!indexDirty && snapshot != null) return snapshot;
        synchronized (this) {
            if (indexDirty || currentSnapshot == null) {
                rebuildIndex();
                indexDirty = false;
            }
            return currentSnapshot;
        }
    }

    /**
     * Takes a snapshot of the currently persisted vectors and rebuilds
     * the HNSW graph in memory against it. Published atomically via the
     * {@code volatile currentSnapshot} field. Must be called under the
     * instance lock.
     */
    private void rebuildIndex() {
        Map<String, RawVectorEntry> entries = root.entries();
        if (entries.isEmpty()) {
            currentSnapshot = JVectorIndexSnapshot.empty();
            return;
        }
        this.currentSnapshot = JVectorIndexSnapshot.build(entries);
    }

    // ================================================================
    // Inner types -- kept here so that readers see the entire story in
    // one file. Both are intentionally static.
    // ================================================================

    /**
     * A single, immutable HNSW snapshot: the graph, the ordinal → id and
     * ordinal → payload mappings, and the original vectors needed to
     * compute exact cosine scores for returned hits.
     *
     * <p>Held in a {@code volatile} on the enclosing class and replaced
     * wholesale whenever the store is mutated. Callers can therefore
     * search without any locking.
     */
    static final class JVectorIndexSnapshot {

        private final ImmutableGraphIndex graph;
        private final FloatVectorValues vectors;
        private final String[] idByOrdinal;
        private final String[] payloadByOrdinal;
        private final float[][] rawByOrdinal;

        private JVectorIndexSnapshot(ImmutableGraphIndex graph,
                                     FloatVectorValues vectors,
                                     String[] idByOrdinal,
                                     String[] payloadByOrdinal,
                                     float[][] rawByOrdinal) {
            this.graph = graph;
            this.vectors = vectors;
            this.idByOrdinal = idByOrdinal;
            this.payloadByOrdinal = payloadByOrdinal;
            this.rawByOrdinal = rawByOrdinal;
        }

        static JVectorIndexSnapshot empty() {
            return new JVectorIndexSnapshot(null,
                    new FloatVectorValues(new float[0][], 0),
                    new String[0], new String[0], new float[0][]);
        }

        static JVectorIndexSnapshot build(Map<String, RawVectorEntry> entries) {
            int n = entries.size();
            String[] ids = new String[n];
            String[] payloads = new String[n];
            float[][] raws = new float[n][];
            int ord = 0;
            int dim = -1;
            for (RawVectorEntry entry : entries.values()) {
                if (dim < 0) {
                    dim = entry.vector().length;
                } else if (entry.vector().length != dim) {
                    throw new IllegalStateException(
                            "inconsistent vector dimensions in store: expected "
                                    + dim + ", got " + entry.vector().length
                                    + " for id=" + entry.id());
                }
                ids[ord] = entry.id();
                payloads[ord] = entry.payload();
                raws[ord] = entry.vector();
                ord++;
            }
            FloatVectorValues ravv = new FloatVectorValues(raws, dim);

            GraphIndexBuilder builder = new GraphIndexBuilder(
                    ravv,
                    SIMILARITY_FUNCTION,
                    HNSW_M,
                    HNSW_EF_CONSTRUCTION,
                    HNSW_NEIGHBOR_OVERFLOW,
                    HNSW_ALPHA,
                    HNSW_ADD_HIERARCHY);
            ImmutableGraphIndex graph = builder.build(ravv);

            return new JVectorIndexSnapshot(graph, ravv, ids, payloads, raws);
        }

        int size() {
            return idByOrdinal.length;
        }

        List<SearchHit> search(float[] query, int k) {
            if (graph == null || size() == 0) return List.of();
            VectorFloat<?> queryVector = VECTOR_TYPE_SUPPORT.createFloatVector(query);
            int effectiveK = Math.min(k, size());
            SearchResult result = GraphSearcher.search(
                    queryVector,
                    effectiveK,
                    vectors,
                    SIMILARITY_FUNCTION,
                    graph,
                    Bits.ALL);
            SearchResult.NodeScore[] nodes = result.getNodes();
            List<SearchHit> hits = new ArrayList<>(nodes.length);
            for (SearchResult.NodeScore ns : nodes) {
                // JVector's COSINE returns the remapped similarity used
                // internally (roughly (1 + cos)/2). Re-compute the real
                // cosine from the raw vectors so that SearchHit.score
                // stays in the documented [-1, 1] interval.
                double cos = COSINE.cosine(query, rawByOrdinal[ns.node]);
                hits.add(new SearchHit(idByOrdinal[ns.node], payloadByOrdinal[ns.node], cos));
            }
            // Approximate scores may not produce a strictly descending
            // run; re-sort on the exact cosine we just computed.
            hits.sort(Comparator.comparingDouble(SearchHit::score).reversed());
            return List.copyOf(hits);
        }
    }

    /**
     * Minimal {@link RandomAccessVectorValues} over a plain
     * {@code float[][]}. Vectors are not shared: each
     * {@link #getVector(int)} call allocates a fresh
     * {@link VectorFloat}, so {@link #copy()} can safely return
     * {@code this} -- no aliasing hazard.
     */
    static final class FloatVectorValues implements RandomAccessVectorValues {

        private final float[][] data;
        private final int dimension;

        FloatVectorValues(float[][] data, int dimension) {
            this.data = data;
            this.dimension = dimension;
        }

        @Override
        public int size() {
            return data.length;
        }

        @Override
        public int dimension() {
            return dimension;
        }

        @Override
        public VectorFloat<?> getVector(int nodeId) {
            return VECTOR_TYPE_SUPPORT.createFloatVector(data[nodeId]);
        }

        @Override
        public boolean isValueShared() {
            return false;
        }

        @Override
        public RandomAccessVectorValues copy() {
            return this;
        }
    }
}
