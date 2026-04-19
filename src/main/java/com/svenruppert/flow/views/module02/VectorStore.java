package com.svenruppert.flow.views.module02;

import java.util.List;

/**
 * A key-value store over dense {@code float[]} vectors that supports
 * top-{@code k} nearest-neighbour search by cosine similarity.
 *
 * <p>The contract is deliberately small: participants write against this
 * interface and swap implementations at runtime (see the "Vector Store
 * Lab" view). Implementations currently in the workshop:
 * <ul>
 *   <li>{@link InMemoryVectorStore} -- linear scan, no persistence, ideal
 *       for understanding the algorithm;</li>
 *   <li>{@link EclipseStoreJVectorStore} -- persistent raw vectors, HNSW
 *       graph rebuilt in memory, the industrial-scale variant.</li>
 * </ul>
 *
 * <h2>Behavioural guarantees</h2>
 * <ul>
 *   <li>{@link #search} returns hits sorted in descending score order.</li>
 *   <li>{@link #add} with an id that already exists <em>overwrites</em>
 *       the previous entry.</li>
 *   <li>{@link #search} with {@code k > size()} returns every entry.</li>
 *   <li>{@link #search} on an empty store returns an empty list.</li>
 *   <li>{@link #size} reflects the number of distinct ids currently
 *       stored.</li>
 * </ul>
 *
 * <p>Implementations are {@link AutoCloseable} so persistent backends can
 * flush and shut down cleanly. The default {@link #close()} is a no-op,
 * which suits in-memory implementations.
 */
public interface VectorStore extends AutoCloseable {

    /**
     * Inserts or updates a single entry.
     *
     * @param id      stable identifier, non-{@code null}
     * @param vector  dense float vector, non-{@code null}; all vectors
     *                in a given store must have the same length
     * @param payload opaque payload string, non-{@code null} (may be empty);
     *                returned verbatim in {@link SearchHit#payload()}
     */
    void add(String id, float[] vector, String payload);

    /**
     * Returns the top-{@code k} entries nearest to {@code queryVector}
     * by cosine similarity.
     *
     * @param queryVector the query, non-{@code null}
     * @param k           requested number of hits; if {@code k >= size()}
     *                    all entries are returned
     * @return hits in descending score order; empty list if the store is empty
     */
    List<SearchHit> search(float[] queryVector, int k);

    /** Returns the number of distinct ids currently stored. */
    int size();

    /** Removes every entry. After this call {@link #size()} is {@code 0}. */
    void clear();

    /** Default {@link #close()} is a no-op, for in-memory implementations. */
    @Override
    default void close() {
        // no-op
    }
}
