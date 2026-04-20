package com.svenruppert.flow.views.module03;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SentenceChunkerTest {

    @Test
    @DisplayName("no chunk ends mid-sentence")
    void respectsSentenceBoundaries() {
        String text = "First sentence. Second sentence. Third sentence. Fourth one.";
        List<Chunk> chunks = new SentenceChunker(40).chunk(text);

        for (Chunk c : chunks) {
            String chunkText = c.text().stripTrailing();
            char last = chunkText.charAt(chunkText.length() - 1);
            assertTrue(last == '.' || last == '!' || last == '?',
                    "chunk ended on non-terminator: '"
                            + chunkText.substring(Math.max(0, chunkText.length() - 10))
                            + "'");
        }
    }

    @Test
    @DisplayName("multiple sentences are packed up to the target size")
    void packsMultipleSentencesUntilTargetSize() {
        // Three sentences, each about 20 chars; targetSize 60 -> one chunk.
        String text = "Alpha beta gamma delta. Epsilon zeta eta theta. Iota kappa lambda mu.";
        List<Chunk> chunks = new SentenceChunker(80).chunk(text);

        assertEquals(1, chunks.size());
        assertEquals(text, chunks.get(0).text());
    }

    @Test
    @DisplayName("a single oversized sentence becomes its own chunk")
    void oversizedSentenceFormsOwnChunk() {
        String longSentence = "This is a deliberately long sentence "
                + "with many clauses that will overshoot any reasonable target "
                + "size all on its own, yet we do not split it because the "
                + "point of sentence chunking is to keep sentences intact.";
        String text = longSentence + " Short one.";
        List<Chunk> chunks = new SentenceChunker(40).chunk(text);

        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).text().length() > 40,
                "oversized sentence should remain as a single chunk");
        assertTrue(chunks.get(1).text().contains("Short"));
    }

    @Test
    @DisplayName("offsets round-trip and cover the whole input")
    void offsetsAreConsistent() {
        String text = "The quick brown fox. Jumps over the lazy dog. "
                + "Pack my box with five dozen liquor jugs. "
                + "Sphinx of black quartz, judge my vow.";
        List<Chunk> chunks = new SentenceChunker(50).chunk(text);
        ChunkerSanityTests.assertIndicesSequential(chunks);
        ChunkerSanityTests.assertOffsetsConsistent(text, chunks);
        ChunkerSanityTests.assertChunksCoverEntireInput(text, chunks);
    }
}
