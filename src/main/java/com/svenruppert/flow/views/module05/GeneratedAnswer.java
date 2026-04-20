package com.svenruppert.flow.views.module05;

import com.svenruppert.flow.views.module04.RetrievalHit;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A finished answer from the RAG pipeline.
 *
 * <p>Carries the full generated text, the chunks the model was shown,
 * the zero-based indices of the chunks the model actually cited, a
 * refusal flag, an optional grounding check result, the total latency
 * from the start of generation to the end of the stream, and -- when
 * the model produced any -- the accumulated thinking/reasoning text
 * emitted alongside the user-facing answer.
 *
 * @param text                full answer text after the stream has ended
 * @param citedChunkIndices   zero-based indices into {@code usedHits}
 *                            that the answer references via
 *                            {@code [Chunk N]} (the answer text uses
 *                            1-based numbers to match the prompt's
 *                            labels)
 * @param usedHits            the retrieval hits fed to the model
 * @param refusalDetected     {@code true} iff the answer looks like a
 *                            honest "I don't know"
 * @param groundingCheck      present iff a {@link GroundingChecker} ran
 * @param latencyMillis       wall-clock latency of the generation step
 * @param thinking            reasoning text emitted by a thinking model
 *                            on its side channel; empty for models
 *                            that do not use thinking mode
 */
public record GeneratedAnswer(
        String text,
        List<Integer> citedChunkIndices,
        List<RetrievalHit> usedHits,
        boolean refusalDetected,
        Optional<GroundingResult> groundingCheck,
        long latencyMillis,
        String thinking) {

    public GeneratedAnswer {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(citedChunkIndices, "citedChunkIndices");
        Objects.requireNonNull(usedHits, "usedHits");
        Objects.requireNonNull(groundingCheck, "groundingCheck");
        Objects.requireNonNull(thinking, "thinking");
        citedChunkIndices = List.copyOf(citedChunkIndices);
        usedHits = List.copyOf(usedHits);
    }

    /**
     * Convenience factory for call sites that do not produce thinking
     * content (tests, non-thinking models). Defaults {@code thinking}
     * to the empty string.
     */
    public static GeneratedAnswer of(String text,
                                     List<Integer> citedChunkIndices,
                                     List<RetrievalHit> usedHits,
                                     boolean refusalDetected,
                                     Optional<GroundingResult> groundingCheck,
                                     long latencyMillis) {
        return new GeneratedAnswer(text, citedChunkIndices, usedHits,
                refusalDetected, groundingCheck, latencyMillis, "");
    }

    /** Returns a copy of this answer with the grounding result attached. */
    public GeneratedAnswer withGrounding(GroundingResult grounding) {
        Objects.requireNonNull(grounding, "grounding");
        return new GeneratedAnswer(
                text, citedChunkIndices, usedHits,
                refusalDetected, Optional.of(grounding), latencyMillis, thinking);
    }
}
