package com.svenruppert.flow.views.module05;

import com.svenruppert.flow.views.module04.RetrievalHit;

import java.util.List;
import java.util.Objects;

/**
 * Balanced grounded-prompt template.
 *
 * <p>Structure of the generated prompt:
 * <ol>
 *   <li>Instruction preamble (cite with {@code [Chunk N]}; refuse with
 *       "I don't know." when the chunks do not contain the answer).</li>
 *   <li>Numbered chunks, each preceded by {@code [Chunk 1]},
 *       {@code [Chunk 2]}, ... .</li>
 *   <li>The question, placed <em>last</em> so the model's attention is
 *       fresh on it after reading the context.</li>
 * </ol>
 *
 * <p>{@link StrictRefusalPromptTemplate} has the same structure but
 * tighter refusal wording; the contrast is the didactic point.
 */
public final class SimpleGroundedPromptTemplate implements PromptTemplate {

    private static final String INSTRUCTIONS = """
            You are answering a question using the provided chunks.
            Cite every factual statement by appending the matching chunk marker, e.g. [Chunk 2].
            If the chunks do not contain the answer, reply exactly with: I don't know.
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
