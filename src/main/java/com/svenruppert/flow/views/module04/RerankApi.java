package com.svenruppert.flow.views.module04;

import java.util.List;
import java.util.Objects;

/**
 * Thin wrapper around Ollama's dedicated reranking endpoint
 * {@code POST /api/rerank}. The endpoint runs a proper cross-encoder
 * model (for example {@code bge-reranker-v2-m3}) against the given
 * query and document batch and returns the indices of the documents
 * in descending-relevance order.
 *
 * <p>Kept as a separate interface from {@link com.svenruppert.flow.views.module01.LlmClient}
 * because not every Ollama build exposes {@code /api/rerank} yet:
 * participants can drop in their own implementation without impact on
 * the rest of module 1.
 */
public interface RerankApi {

    /**
     * @param model     reranker model name (e.g. {@code bge-reranker-v2-m3})
     * @param query     the user query
     * @param documents candidate passages in submission order
     * @param topN      how many of the highest-scoring documents to
     *                  request
     * @return list of (index, score) pairs, ordered by descending score;
     *         empty if the Ollama build does not expose
     *         {@code /api/rerank} or any other failure happened
     */
    List<RerankScore> rerank(String model, String query,
                             List<String> documents, int topN);

    /**
     * A single entry in an Ollama {@code /api/rerank} response:
     *
     * <pre>{@code { "index": 2, "relevance_score": 0.87 }}</pre>
     *
     * @param index index into the original {@code documents} list
     * @param score relevance score, higher is better
     */
    record RerankScore(int index, double score) {
        public RerankScore {
            if (index < 0) {
                throw new IllegalArgumentException("index must be >= 0, got " + index);
            }
        }

        /** Convenience factory used in tests and parsers. */
        public static RerankScore of(int index, double score) {
            return new RerankScore(index, score);
        }
    }

    /** Null-object: returns an empty list for any call. */
    RerankApi UNAVAILABLE = (model, query, documents, topN) -> {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(documents, "documents");
        return List.of();
    };
}
