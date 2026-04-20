package com.svenruppert.flow.views.module04;

import java.util.List;

/**
 * Takes first-stage candidates and reorders them against a more
 * expensive-but-more-accurate scorer, returns the top-{@code k}.
 *
 * <p>One implementation ships with the module:
 * <ul>
 *   <li>{@link LlmJudgeReranker}: a general instruction-tuned model
 *       prompted to emit a 0-10 relevance score per candidate.</li>
 * </ul>
 *
 * <p>A true cross-encoder reranker (the canonical second-stage choice
 * in production RAG) is intentionally absent: Ollama exposes no
 * dedicated reranking endpoint, so a workshop strictly limited to
 * Ollama has no honest path to one. The cross-encoder route is
 * covered conceptually on the slides; production code reaches it via
 * ONNX Runtime, outside the workshop's no-native-code rule.
 */
public interface Reranker {

    List<RetrievalHit> rerank(String query, List<RetrievalHit> candidates, int k);
}
