package com.svenruppert.flow.views.module03;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared sanity assertions used by every concrete chunker test.
 *
 * <p>These are invariants the {@link Chunker} contract promises:
 * <ul>
 *   <li>every chunk's stored {@code text} matches the corresponding
 *       substring of the source ({@link Chunk#matchesSource(String)});</li>
 *   <li>chunk indices run {@code 0, 1, 2, ...} without gaps;</li>
 *   <li>the union of chunk ranges covers the entire source (possibly
 *       with overlap).</li>
 * </ul>
 */
final class ChunkerSanityTests {

    private ChunkerSanityTests() {
    }

    static void assertChunksCoverEntireInput(String text, List<Chunk> chunks) {
        if (text.isEmpty()) {
            assertTrue(chunks.isEmpty(), "empty input must yield no chunks");
            return;
        }
        boolean[] covered = new boolean[text.length()];
        for (Chunk c : chunks) {
            for (int i = c.startOffset(); i < c.endOffset(); i++) {
                covered[i] = true;
            }
        }
        for (int i = 0; i < covered.length; i++) {
            assertTrue(covered[i], "position " + i + " is not covered by any chunk");
        }
    }

    static void assertOffsetsConsistent(String text, List<Chunk> chunks) {
        for (Chunk c : chunks) {
            assertTrue(c.matchesSource(text),
                    "chunk " + c.index() + " text does not match substring("
                            + c.startOffset() + ", " + c.endOffset() + ")");
        }
    }

    static void assertIndicesSequential(List<Chunk> chunks) {
        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).index(),
                    "chunk at position " + i + " must carry index " + i);
        }
    }
}
