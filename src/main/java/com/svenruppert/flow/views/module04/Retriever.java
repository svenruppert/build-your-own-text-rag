package com.svenruppert.flow.views.module04;

import java.util.List;

/**
 * Returns the top-{@code k} candidate chunks for a free-form query
 * string. Implementations are interchangeable: participants swap
 * {@link VectorRetriever}, {@link BM25Retriever} and {@link HybridRetriever}
 * at runtime in the Module 04 view and watch the result set change.
 */
public interface Retriever {

    /**
     * @param query user query
     * @param k     requested number of hits; implementations may return
     *              fewer if the underlying corpus does not have enough
     *              candidates
     * @return non-{@code null} list, descending by {@link RetrievalHit#score()}
     */
    List<RetrievalHit> retrieve(String query, int k);
}
