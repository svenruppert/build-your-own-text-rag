package com.svenruppert.flow.views.module03;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedSizeChunkerTest {

    @Test
    @DisplayName("100 chars / size 25 -> 4 chunks")
    void producesCorrectNumberOfChunks() {
        String text = "x".repeat(100);
        List<Chunk> chunks = new FixedSizeChunker(25).chunk(text);
        assertEquals(4, chunks.size());
        for (Chunk c : chunks) {
            assertEquals(25, c.endOffset() - c.startOffset());
        }
    }

    @Test
    @DisplayName("last chunk can be shorter when length is not a multiple")
    void lastChunkCanBeShorter() {
        String text = "x".repeat(103);
        List<Chunk> chunks = new FixedSizeChunker(25).chunk(text);
        assertEquals(5, chunks.size());
        assertEquals(3, chunks.get(chunks.size() - 1).text().length());
    }

    @Test
    @DisplayName("empty input produces empty list")
    void emptyInputProducesEmptyList() {
        assertTrue(new FixedSizeChunker(10).chunk("").isEmpty());
    }

    @Test
    @DisplayName("chunkSize <= 0 is rejected")
    void invalidChunkSizeThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new FixedSizeChunker(0));
        assertThrows(IllegalArgumentException.class, () -> new FixedSizeChunker(-5));
    }

    @Test
    @DisplayName("offsets round-trip and cover the whole input")
    void offsetsAreConsistent() {
        String text = "The quick brown fox jumps over the lazy dog. ".repeat(10);
        List<Chunk> chunks = new FixedSizeChunker(17).chunk(text);

        ChunkerSanityTests.assertIndicesSequential(chunks);
        ChunkerSanityTests.assertOffsetsConsistent(text, chunks);
        ChunkerSanityTests.assertChunksCoverEntireInput(text, chunks);
    }
}
