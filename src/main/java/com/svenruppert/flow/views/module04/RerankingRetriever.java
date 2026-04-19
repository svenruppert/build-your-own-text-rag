package com.svenruppert.flow.views.module04;

import java.util.List;
import java.util.Objects;

/**
 * Two-stage retriever: ask an inner {@link Retriever} for
 * {@code firstStageK} candidates, then hand those to a
 * {@link Reranker} that returns the final top-{@code k}.
 *
 * <p>This is the textbook retrieve-then-rerank pipeline: the inner
 * retriever is tuned for recall (cheap and permissive), the reranker
 * for precision (expensive and strict).
 */
public final class RerankingRetriever implements Retriever {

    private final Retriever inner;
    private final Reranker reranker;
    private final int firstStageK;

    public RerankingRetriever(Retriever inner, Reranker reranker, int firstStageK) {
        this.inner = Objects.requireNonNull(inner, "inner");
        this.reranker = Objects.requireNonNull(reranker, "reranker");
        if (firstStageK <= 0) {
            throw new IllegalArgumentException("firstStageK must be > 0, got " + firstStageK);
        }
        this.firstStageK = firstStageK;
    }

    @Override
    public List<RetrievalHit> retrieve(String query, int k) {
        Objects.requireNonNull(query, "query");
        if (k <= 0) return List.of();
        List<RetrievalHit> candidates = inner.retrieve(query, firstStageK);
        if (candidates.isEmpty()) return List.of();
        return reranker.rerank(query, candidates, k);
    }
}
