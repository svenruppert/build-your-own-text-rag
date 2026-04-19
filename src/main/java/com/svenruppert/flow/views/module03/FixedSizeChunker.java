package com.svenruppert.flow.views.module03;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fixed-character-length chunker. The text is cut into blocks of exactly
 * {@code chunkSize} characters; the final chunk may be shorter if the
 * input length is not a multiple of {@code chunkSize}. No overlap.
 *
 * <p>This is the naive baseline every workshop participant sees first:
 * simple, fast, and usually insufficient because it severs sentences
 * mid-word. The other chunkers exist to improve on exactly that.
 */
public final class FixedSizeChunker implements Chunker {

    private final int chunkSize;

    public FixedSizeChunker(int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException(
                    "chunkSize must be > 0, got " + chunkSize);
        }
        this.chunkSize = chunkSize;
    }

    @Override
    public List<Chunk> chunk(String text) {
        Objects.requireNonNull(text, "text");
        if (text.isEmpty()) return List.of();

        int length = text.length();
        int estimatedChunks = (length + chunkSize - 1) / chunkSize;
        List<Chunk> chunks = new ArrayList<>(estimatedChunks);

        int index = 0;
        for (int start = 0; start < length; start += chunkSize) {
            int end = Math.min(start + chunkSize, length);
            chunks.add(new Chunk(index++, text.substring(start, end), start, end));
        }
        return List.copyOf(chunks);
    }
}
