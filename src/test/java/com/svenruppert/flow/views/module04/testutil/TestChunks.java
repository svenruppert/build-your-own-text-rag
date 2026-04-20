package com.svenruppert.flow.views.module04.testutil;

import com.svenruppert.flow.views.module03.Chunk;

/**
 * Handy chunk factories for tests. The offsets are synthetic -- they
 * only need to satisfy {@link Chunk}'s {@code startOffset >= 0} and
 * {@code endOffset > startOffset} invariants.
 */
public final class TestChunks {

    private TestChunks() {
    }

    public static Chunk of(int index, String text) {
        // Each chunk sits in its own synthetic [100*index, 100*index + text.length)
        // window -- enough to satisfy Chunk's invariants without overlap.
        int start = 100 * index;
        return new Chunk(index, text, start, start + Math.max(1, text.length()));
    }
}
