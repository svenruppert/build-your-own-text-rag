package com.svenruppert.flow.views.module03;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Like {@link FixedSizeChunker}, but adjacent chunks share the last
 * {@code overlap} characters with the next chunk. The overlap keeps
 * information that crosses a chunk boundary recoverable in retrieval --
 * a sentence whose first half falls at the end of chunk N and whose
 * second half starts chunk N+1 still appears intact inside one of
 * the chunks. This is the workhorse chunker of most production RAG
 * pipelines.
 *
 * <p>Stride between successive chunk starts is {@code chunkSize -
 * overlap}. With {@code overlap == 0} the behaviour collapses to
 * {@link FixedSizeChunker}.
 */
public final class OverlappingChunker implements Chunker {

    private final int chunkSize;
    private final int overlap;
    private final int stride;

    public OverlappingChunker(int chunkSize, int overlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException(
                    "chunkSize must be > 0, got " + chunkSize);
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException(
                    "overlap must be in [0, chunkSize), got " + overlap
                            + " for chunkSize " + chunkSize);
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
        this.stride = chunkSize - overlap;
    }

    @Override
    public List<Chunk> chunk(String text) {
        Objects.requireNonNull(text, "text");
        if (text.isEmpty()) return List.of();

        int length = text.length();
        List<Chunk> chunks = new ArrayList<>();

        int index = 0;
        int start = 0;
        while (start < length) {
            int end = Math.min(start + chunkSize, length);
            chunks.add(new Chunk(index++, text.substring(start, end), start, end));
            if (end == length) break;
            start += stride;
        }
        return List.copyOf(chunks);
    }
}
