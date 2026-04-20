package com.svenruppert.flow.views.module03;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverlappingChunkerTest {

    @Test
    @DisplayName("overlap of 0 behaves like FixedSizeChunker")
    void overlapOfZeroBehavesLikeFixedSize() {
        String text = "x".repeat(100);
        List<Chunk> overlapZero = new OverlappingChunker(25, 0).chunk(text);
        List<Chunk> fixed = new FixedSizeChunker(25).chunk(text);
        assertEquals(fixed.size(), overlapZero.size());
        for (int i = 0; i < fixed.size(); i++) {
            assertEquals(fixed.get(i).startOffset(), overlapZero.get(i).startOffset());
            assertEquals(fixed.get(i).endOffset(), overlapZero.get(i).endOffset());
        }
    }

    @Test
    @DisplayName("adjacent chunks share `overlap` characters")
    void overlapReducesUniqueCoverage() {
        String text = "abcdefghij".repeat(20); // 200 chars
        int chunkSize = 100;
        int overlap = 20;
        List<Chunk> chunks = new OverlappingChunker(chunkSize, overlap).chunk(text);

        assertTrue(chunks.size() >= 2, "expected multiple chunks for an overlap test");
        for (int i = 1; i < chunks.size(); i++) {
            int sharedStart = chunks.get(i).startOffset();
            int sharedEnd = chunks.get(i - 1).endOffset();
            int sharedLength = sharedEnd - sharedStart;
            if (i < chunks.size() - 1) {
                assertEquals(overlap, sharedLength,
                        "middle chunks should share exactly `overlap` chars");
            } else {
                // Tail chunk may be truncated against the end of the text;
                // the overlap can only shrink, never grow.
                assertTrue(sharedLength <= overlap);
            }
        }
    }

    @Test
    @DisplayName("overlap >= chunkSize is rejected")
    void invalidOverlapThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new OverlappingChunker(20, 20));
        assertThrows(IllegalArgumentException.class, () -> new OverlappingChunker(20, 25));
        assertThrows(IllegalArgumentException.class, () -> new OverlappingChunker(20, -1));
    }

    @Test
    @DisplayName("offsets round-trip and cover the whole input")
    void offsetsAreConsistent() {
        String text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. ".repeat(5);
        List<Chunk> chunks = new OverlappingChunker(50, 10).chunk(text);
        ChunkerSanityTests.assertIndicesSequential(chunks);
        ChunkerSanityTests.assertOffsetsConsistent(text, chunks);
        ChunkerSanityTests.assertChunksCoverEntireInput(text, chunks);
    }
}
