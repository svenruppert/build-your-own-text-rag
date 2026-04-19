package com.svenruppert.flow.views.module05;

import java.util.Objects;

/**
 * The output of a {@link GroundingChecker}: a verdict plus a short
 * natural-language rationale.
 *
 * @param verdict   one of {@link Verdict}
 * @param rationale a short one-sentence rationale, or the empty string
 *                  when the checker's reply could not be parsed
 */
public record GroundingResult(Verdict verdict, String rationale) {

    public GroundingResult {
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(rationale, "rationale");
    }

    public static GroundingResult unknown() {
        return new GroundingResult(Verdict.UNKNOWN, "");
    }

    /**
     * How well an answer is supported by the retrieved chunks.
     *
     * <ul>
     *   <li>{@link #GROUNDED} -- every claim is directly supported</li>
     *   <li>{@link #PARTIAL}  -- some claims are supported, others are not</li>
     *   <li>{@link #NOT_GROUNDED} -- the answer is not supported at all</li>
     *   <li>{@link #UNKNOWN} -- the checker did not return a parseable reply</li>
     * </ul>
     */
    public enum Verdict {
        GROUNDED, PARTIAL, NOT_GROUNDED, UNKNOWN
    }
}
