package com.svenruppert.flow.views.module04;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.views.module03.Chunk;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Combines the ranked lists produced by a {@link VectorRetriever} and
 * a {@link BM25Retriever} using a {@link FusionStrategy}.
 *
 * <h2>Fusion strategies</h2>
 * <ul>
 *   <li>{@link FusionStrategy.ReciprocalRankFusion}:
 *       {@code score_i = sum over retrievers (1 / (rrfK + rank_i))}.
 *       Ignores the scale of the underlying scores, so mixing BM25 (raw
 *       scores, scale depends on corpus) with cosine (in [-1, 1]) works
 *       out of the box.</li>
 *   <li>{@link FusionStrategy.WeightedScoreFusion}: normalise each
 *       retriever's scores to {@code [0, 1]} using the batch maximum,
 *       then combine as {@code w_v * v + w_b * b}. Sensitive to score
 *       scale, and that sensitivity is exactly what the normalisation
 *       step exists to tame.</li>
 * </ul>
 *
 * <p>First-stage recall is tuned via {@code recallK}: each underlying
 * retriever is asked for its top {@code recallK} hits, then the fusion
 * condenses to the caller-requested {@code k}.
 */
public final class HybridRetriever implements Retriever, HasLogger {

    // Kept as Retriever internally so tests can plug in stubs; the
    // public constructor preserves the (VectorRetriever, BM25Retriever)
    // signature that documents the intended production wiring.
    private final Retriever vectorRetriever;
    private final Retriever bm25Retriever;
    private final FusionStrategy fusionStrategy;
    private final int recallK;

    public HybridRetriever(VectorRetriever vectorRetriever,
                           BM25Retriever bm25Retriever,
                           FusionStrategy fusionStrategy,
                           int recallK) {
        this((Retriever) vectorRetriever, (Retriever) bm25Retriever, fusionStrategy, recallK);
    }

    /**
     * Package-private constructor used by tests to inject stub
     * retrievers without having to assemble a real
     * {@link VectorRetriever} / {@link BM25Retriever} stack.
     */
    HybridRetriever(Retriever vectorRetriever,
                    Retriever bm25Retriever,
                    FusionStrategy fusionStrategy,
                    int recallK) {
        this.vectorRetriever = Objects.requireNonNull(vectorRetriever, "vectorRetriever");
        this.bm25Retriever = Objects.requireNonNull(bm25Retriever, "bm25Retriever");
        this.fusionStrategy = Objects.requireNonNull(fusionStrategy, "fusionStrategy");
        if (recallK <= 0) {
            throw new IllegalArgumentException("recallK must be > 0, got " + recallK);
        }
        this.recallK = recallK;
    }

    @Override
    public List<RetrievalHit> retrieve(String query, int k) {
        Objects.requireNonNull(query, "query");
        if (k <= 0) return List.of();

        List<RetrievalHit> vectorHits = vectorRetriever.retrieve(query, recallK);
        List<RetrievalHit> bm25Hits = bm25Retriever.retrieve(query, recallK);

        Map<Chunk, Double> fused = switch (fusionStrategy) {
            case FusionStrategy.ReciprocalRankFusion rrf -> fuseByRrf(vectorHits, bm25Hits, rrf.rrfK());
            case FusionStrategy.WeightedScoreFusion w -> fuseByWeights(
                    vectorHits, bm25Hits, w.vectorWeight(), w.bm25Weight());
        };

        return fused.entrySet().stream()
                .map(entry -> new RetrievalHit(entry.getKey(), entry.getValue(), "hybrid"))
                .sorted(Comparator.comparingDouble(RetrievalHit::score).reversed())
                .limit(k)
                .toList();
    }

    // --------- fusion implementations ----------------------------------

    private static Map<Chunk, Double> fuseByRrf(List<RetrievalHit> a,
                                                List<RetrievalHit> b,
                                                double rrfK) {
        Map<Chunk, Double> accum = new HashMap<>();
        addRrfContributions(accum, a, rrfK);
        addRrfContributions(accum, b, rrfK);
        return accum;
    }

    private static void addRrfContributions(Map<Chunk, Double> accum,
                                            List<RetrievalHit> hits,
                                            double rrfK) {
        for (int rank = 0; rank < hits.size(); rank++) {
            Chunk chunk = hits.get(rank).chunk();
            double contribution = 1.0 / (rrfK + rank);
            accum.merge(chunk, contribution, Double::sum);
        }
    }

    private static Map<Chunk, Double> fuseByWeights(List<RetrievalHit> vectorHits,
                                                    List<RetrievalHit> bm25Hits,
                                                    double vectorWeight,
                                                    double bm25Weight) {
        Map<Chunk, Double> normVector = normalise(vectorHits);
        Map<Chunk, Double> normBm25 = normalise(bm25Hits);

        Map<Chunk, Double> accum = new HashMap<>();
        normVector.forEach((chunk, v) ->
                accum.merge(chunk, vectorWeight * v, Double::sum));
        normBm25.forEach((chunk, v) ->
                accum.merge(chunk, bm25Weight * v, Double::sum));
        return accum;
    }

    /**
     * Normalises the scores in a single retriever batch to {@code [0, 1]}
     * by dividing by the batch maximum. If every score is zero (or
     * negative after normalisation), returns them unchanged -- the
     * fusion then behaves as if the batch contributed nothing.
     */
    private static Map<Chunk, Double> normalise(List<RetrievalHit> hits) {
        double max = hits.stream()
                .mapToDouble(RetrievalHit::score)
                .max()
                .orElse(0.0);
        Map<Chunk, Double> out = new HashMap<>();
        if (max <= 0.0) {
            for (RetrievalHit h : hits) out.put(h.chunk(), 0.0);
            return out;
        }
        for (RetrievalHit h : hits) {
            out.put(h.chunk(), h.score() / max);
        }
        return out;
    }
}
