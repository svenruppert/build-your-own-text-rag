package com.svenruppert.flow.views.module04;

/**
 * How {@link HybridRetriever} merges a vector-retrieved candidate list
 * with a BM25-retrieved candidate list into a single ranked list.
 *
 * <p>Sealed so the merge logic can use an exhaustive
 * {@code switch} expression; every new strategy must be added to this
 * file and nowhere else.
 *
 * <h2>Formulas</h2>
 * <ul>
 *   <li>Reciprocal Rank Fusion (RRF):
 *       {@code score_i = sum over retrievers (1 / (rrfK + rank_i))}.
 *       Ignores score scale entirely; only the rank each retriever
 *       assigned matters. The {@code rrfK} constant (typically 60)
 *       dampens the influence of the very top positions so a single
 *       retriever cannot steamroll the fusion.</li>
 *   <li>Weighted-score: normalise each retriever's scores to
 *       {@code [0, 1]} by dividing by the batch maximum, then combine
 *       as {@code w_v * v + w_b * b}. Sensitive to score scale, so
 *       the normalisation step matters.</li>
 * </ul>
 */
public sealed interface FusionStrategy {

    /**
     * RRF strategy. Default {@code rrfK} is 60 -- the value used in the
     * original paper and in most production code; adjust downward for
     * corpora where fewer retrievers should dominate the top hits.
     */
    record ReciprocalRankFusion(double rrfK) implements FusionStrategy {
        public static ReciprocalRankFusion withDefaultK() {
            return new ReciprocalRankFusion(60.0);
        }
    }

    /**
     * Weighted-score strategy. Both weights should be non-negative;
     * they are typically in {@code [0, 1]} but are not required to
     * sum to 1 -- the hybrid retriever does not renormalise.
     */
    record WeightedScoreFusion(double vectorWeight, double bm25Weight)
            implements FusionStrategy {
    }
}
