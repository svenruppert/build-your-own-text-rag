package com.svenruppert.flow.views.module03;

import java.util.Map;
import java.util.Objects;

/**
 * A contiguous slice of some larger text, produced by a {@link Chunker}.
 *
 * <p>The {@code startOffset}/{@code endOffset} pair locates the chunk in
 * the original input ({@code startOffset} inclusive, {@code endOffset}
 * exclusive). {@link #matchesSource(String)} verifies that the stored
 * {@code text} really is the substring -- an invariant every
 * {@link Chunker} must preserve.
 *
 * <p>An optional {@link #metadata()} map lets specialised chunkers
 * (most notably {@link StructureAwareChunker}) attach structured
 * information per chunk, such as the heading path. Basic chunkers leave
 * it empty.
 *
 * @param index       running number across the chunker's output, 0-based
 * @param text        the chunk's text, non-{@code null}
 * @param startOffset inclusive start in the source text, {@code >= 0}
 * @param endOffset   exclusive end in the source text, {@code > startOffset}
 * @param metadata    optional metadata; {@code null} is normalised to
 *                    an empty, immutable map
 */
public record Chunk(int index, String text, int startOffset, int endOffset,
                    Map<String, Object> metadata) {

    /** Metadata key: heading path, e.g. {@code "Chapter 1 / Section 1.2"}. */
    public static final String HEADING_PATH = "heading-path";

    public Chunk {
        Objects.requireNonNull(text, "text");
        if (startOffset < 0) {
            throw new IllegalArgumentException("startOffset must be >= 0, got " + startOffset);
        }
        if (endOffset <= startOffset) {
            throw new IllegalArgumentException(
                    "endOffset (" + endOffset + ") must be > startOffset (" + startOffset + ")");
        }
        metadata = (metadata == null) ? Map.of() : Map.copyOf(metadata);
    }

    /** Convenience constructor for chunkers that do not produce metadata. */
    public Chunk(int index, String text, int startOffset, int endOffset) {
        this(index, text, startOffset, endOffset, Map.of());
    }

    /**
     * {@code true} iff the chunk's {@link #text} matches the substring
     * of {@code originalText} in {@code [startOffset, endOffset)}.
     */
    public boolean matchesSource(String originalText) {
        Objects.requireNonNull(originalText, "originalText");
        if (endOffset > originalText.length()) return false;
        return originalText.substring(startOffset, endOffset).equals(text);
    }
}
