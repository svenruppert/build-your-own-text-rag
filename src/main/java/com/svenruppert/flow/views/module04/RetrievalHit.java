package com.svenruppert.flow.views.module04;

import com.svenruppert.flow.views.module03.Chunk;

import java.util.Objects;

/**
 * A single hit from a {@link Retriever}: the matched chunk, its score,
 * and a short label identifying which stage of the pipeline produced
 * this hit. The label makes result rows in the UI self-describing and
 * lets downstream consumers (for example rerankers) attach provenance
 * to the hits they emit.
 *
 * <p>Valid {@code sourceLabel} values used in this module:
 * <ul>
 *   <li>{@code "vector"}</li>
 *   <li>{@code "bm25"}</li>
 *   <li>{@code "hybrid"}</li>
 *   <li>{@code "llm-judge-reranked"}</li>
 *   <li>{@code "bge-reranked"}</li>
 * </ul>
 *
 * @param chunk       the matched chunk, non-{@code null}
 * @param score       source-specific score; higher is better
 * @param sourceLabel short provenance label, non-{@code null}
 */
public record RetrievalHit(Chunk chunk, double score, String sourceLabel) {

    public RetrievalHit {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(sourceLabel, "sourceLabel");
    }
}
