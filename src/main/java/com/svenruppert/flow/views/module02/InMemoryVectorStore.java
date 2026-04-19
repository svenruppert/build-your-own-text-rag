package com.svenruppert.flow.views.module02;

import com.svenruppert.dependencies.core.logger.HasLogger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Naive in-memory {@link VectorStore}: linear scan over a
 * {@link ConcurrentHashMap}. O(n) per query. No persistence -- all state
 * is lost on restart.
 *
 * <p>This is the first of two stores in the "Vector Store Lab": it is
 * the didactic reference point. Participants should be able to read it
 * top to bottom in a minute and understand cosine search without any
 * HNSW or indexing vocabulary.
 */
public final class InMemoryVectorStore implements VectorStore, HasLogger {

    private final Similarity similarity;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public InMemoryVectorStore(Similarity similarity) {
        this.similarity = Objects.requireNonNull(similarity, "similarity");
    }

    @Override
    public void add(String id, float[] vector, String payload) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(vector, "vector");
        Objects.requireNonNull(payload, "payload");
        if (vector.length == 0) {
            throw new IllegalArgumentException("vector must not be empty");
        }
        // Defensive copy: callers may reuse float[] buffers.
        float[] copy = vector.clone();
        entries.put(id, new Entry(copy, payload));
    }

    @Override
    public List<SearchHit> search(float[] queryVector, int k) {
        Objects.requireNonNull(queryVector, "queryVector");
        if (k <= 0 || entries.isEmpty()) {
            return List.of();
        }
        // Bounded min-heap of size k: the smallest score sits at the top,
        // and we pop it whenever a better candidate arrives. This keeps
        // memory at O(k) rather than O(n).
        PriorityQueue<SearchHit> heap =
                new PriorityQueue<>(Comparator.comparingDouble(SearchHit::score));
        for (var e : entries.entrySet()) {
            String id = e.getKey();
            Entry entry = e.getValue();
            double score = similarity.cosine(queryVector, entry.vector());
            SearchHit hit = new SearchHit(id, entry.payload(), score);
            if (heap.size() < k) {
                heap.offer(hit);
            } else if (score > heap.peek().score()) {
                heap.poll();
                heap.offer(hit);
            }
        }
        List<SearchHit> result = new ArrayList<>(heap);
        // Descending by score -- the contract promises best-first order.
        result.sort(Comparator.comparingDouble(SearchHit::score).reversed());
        return List.copyOf(result);
    }

    @Override
    public int size() {
        return entries.size();
    }

    @Override
    public void clear() {
        entries.clear();
    }

    /** Package-private value holder; not part of the public surface. */
    private record Entry(float[] vector, String payload) {
    }
}
