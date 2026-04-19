package com.svenruppert.flow.views.module04;

import com.svenruppert.dependencies.core.logger.HasLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cross-encoder reranker backed by Ollama's dedicated
 * {@code /api/rerank} endpoint (reached via {@link RerankApi}).
 *
 * <h2>Architectural note</h2>
 * A cross-encoder processes query and passage together in one forward
 * pass of a small model trained specifically for relevance ranking.
 * {@code bge-reranker-v2-m3} is such a model, hosted by Ollama through
 * the dedicated {@code /api/rerank} endpoint. Architecturally it is
 * the same family of models that runs inside production Java services
 * via ONNX Runtime -- here we run it in a separate process via HTTP,
 * which trades a little latency for operational simplicity.
 *
 * <h2>Degraded behaviour</h2>
 * When {@link RerankApi#rerank} returns an empty list (older Ollama
 * builds do not expose {@code /api/rerank}), the reranker passes the
 * first {@code k} candidates through unchanged. A single warning is
 * logged per instance to avoid flooding the log on repeated searches.
 */
public final class BgeReranker implements Reranker, HasLogger {

    public static final String DEFAULT_MODEL = "bge-reranker-v2-m3";

    private final RerankApi rerankApi;
    private final String model;

    /** Prevents repeated "endpoint unavailable" warnings. */
    private final AtomicBoolean unavailableLogged = new AtomicBoolean(false);

    public BgeReranker(RerankApi rerankApi) {
        this(rerankApi, DEFAULT_MODEL);
    }

    public BgeReranker(RerankApi rerankApi, String model) {
        this.rerankApi = Objects.requireNonNull(rerankApi, "rerankApi");
        this.model = Objects.requireNonNull(model, "model");
    }

    @Override
    public List<RetrievalHit> rerank(String query, List<RetrievalHit> candidates, int k) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(candidates, "candidates");
        if (k <= 0 || candidates.isEmpty()) return List.of();

        List<String> texts = candidates.stream()
                .map(c -> c.chunk().text())
                .toList();

        List<RerankApi.RerankScore> results = rerankApi.rerank(
                model, query, texts, candidates.size());

        if (results.isEmpty()) {
            if (unavailableLogged.compareAndSet(false, true)) {
                logger().warn("rerank API returned no results -- "
                        + "passing the first {} candidates through unchanged", k);
            }
            return candidates.stream().limit(k).toList();
        }

        List<RetrievalHit> out = new ArrayList<>(results.size());
        for (RerankApi.RerankScore rs : results) {
            if (rs.index() < 0 || rs.index() >= candidates.size()) continue;
            RetrievalHit original = candidates.get(rs.index());
            out.add(new RetrievalHit(original.chunk(), rs.score(), "bge-reranked"));
        }
        return out.stream().limit(k).toList();
    }
}
