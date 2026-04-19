package com.svenruppert.flow.views.module03;

import java.util.List;

/**
 * Splits a plain-text input into an ordered list of {@link Chunk}s.
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>The returned list is sorted by {@code startOffset}; indices
 *       run {@code 0, 1, 2, ...} with no gaps.</li>
 *   <li>Each chunk's {@code [startOffset, endOffset)} lies inside the
 *       input and satisfies {@link Chunk#matchesSource(String)}.</li>
 *   <li>Adjacent chunks are allowed to overlap -- overlap is part of
 *       the semantics of some implementations (for example
 *       {@link OverlappingChunker}).</li>
 *   <li>An empty input yields an empty list.</li>
 * </ul>
 *
 * <p>Implementations are stateless after construction and therefore
 * safe to share across threads, with one caveat: any per-call use of a
 * {@link java.text.BreakIterator} must create a fresh instance each
 * call because {@code BreakIterator} is not thread-safe.
 */
public interface Chunker {

    /**
     * Splits {@code text} into chunks according to the strategy of this
     * {@link Chunker}.
     *
     * @param text non-{@code null} plain text
     * @return ordered list of chunks; empty for empty input
     */
    List<Chunk> chunk(String text);
}
