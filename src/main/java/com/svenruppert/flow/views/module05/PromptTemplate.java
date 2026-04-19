package com.svenruppert.flow.views.module05;

import com.svenruppert.flow.views.module04.RetrievalHit;

import java.util.List;

/**
 * Shapes a prompt out of a query plus its retrieved chunks.
 *
 * <p>The returned prompt must embed the query and the hits such that
 * the model can cite each hit as {@code [Chunk N]} with {@code N}
 * starting at 1. Implementations control the exact phrasing,
 * delimiters and refusal instructions.
 *
 * <p>Two concrete implementations ship with the module:
 * {@link SimpleGroundedPromptTemplate} (balanced wording) and
 * {@link StrictRefusalPromptTemplate} (aggressive refusal wording).
 * The contrast is the didactic point of the module.
 */
public interface PromptTemplate {

    String buildPrompt(String query, List<RetrievalHit> hits);
}
