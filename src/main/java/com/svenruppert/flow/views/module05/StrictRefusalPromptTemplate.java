package com.svenruppert.flow.views.module05;

import com.svenruppert.flow.views.module04.RetrievalHit;

import java.util.List;
import java.util.Objects;

/**
 * Aggressive-refusal variant of the grounded-prompt template.
 *
 * <p>Same overall layout as {@link SimpleGroundedPromptTemplate}
 * (instructions, numbered chunks, question last) but the refusal
 * wording is sharper: the model is told to reply with "I don't know."
 * if <em>any</em> part of an answer is not directly supported by a
 * chunk. On hallucination-prone corpora this tightens behaviour
 * visibly; the trade-off is a higher rate of unnecessary refusals on
 * legitimate answers.
 */
public final class StrictRefusalPromptTemplate implements PromptTemplate {

    private static final String INSTRUCTIONS = """
            Answer ONLY from the provided chunks.
            Cite every factual statement by appending the matching chunk marker, e.g. [Chunk 2].
            If any part of your answer is not directly supported by a chunk, say 'I don't know.' instead.
            """;

    @Override
    public String buildPrompt(String query, List<RetrievalHit> hits) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(hits, "hits");

        StringBuilder sb = new StringBuilder();
        sb.append(INSTRUCTIONS).append('\n');

        sb.append("=== CONTEXT ===\n");
        int number = 1;
        for (RetrievalHit hit : hits) {
            sb.append("[Chunk ").append(number++).append("]\n");
            sb.append(hit.chunk().text()).append("\n\n");
        }

        sb.append("=== QUESTION ===\n");
        sb.append(query).append('\n');
        return sb.toString();
    }
}
