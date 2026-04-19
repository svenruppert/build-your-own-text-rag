package com.svenruppert.flow.views.module04;

import java.util.List;

/**
 * Takes first-stage candidates and reorders them against a more
 * expensive-but-more-accurate scorer, returns the top-{@code k}.
 *
 * <p>Two implementations ship with the module:
 * <ul>
 *   <li>{@link LlmJudgeReranker}: a general instruction-tuned model
 *       prompted to emit a 0-10 relevance score per candidate.</li>
 *   <li>{@link BgeReranker}: a cross-encoder model hosted by Ollama's
 *       {@code /api/rerank} endpoint.</li>
 * </ul>
 */
public interface Reranker {

    List<RetrievalHit> rerank(String query, List<RetrievalHit> candidates, int k);
}
