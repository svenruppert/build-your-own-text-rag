package com.svenruppert.flow.views.module03;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureAwareChunkerTest {

    @Test
    @DisplayName("chunk boundaries snap to heading transitions")
    void chunksRespectHeadingBoundaries() {
        // Plain text layout:
        //   [0]         "Intro\n\n"          -- 7 chars
        //   [7]         "Section A\n\n"      -- 11 chars
        //   [18]        "Body A.\n\n"        -- 9 chars
        //   [27]        "Section B\n\n"      -- 11 chars
        //   [38]        "Body B."            -- 7 chars  (length 45)
        String text = "Intro\n\nSection A\n\nBody A.\n\nSection B\n\nBody B.";
        List<HeadingInfo> headings = List.of(
                new HeadingInfo(1, "Intro", 0),
                new HeadingInfo(2, "Section A", 7),
                new HeadingInfo(2, "Section B", 27)
        );

        List<Chunk> chunks = new StructureAwareChunker(200, headings).chunk(text);

        // One chunk per heading section, none crossing a boundary.
        assertEquals(3, chunks.size());
        assertEquals(0, chunks.get(0).startOffset());
        assertEquals(7, chunks.get(0).endOffset());
        assertEquals(7, chunks.get(1).startOffset());
        assertEquals(27, chunks.get(1).endOffset());
        assertEquals(27, chunks.get(2).startOffset());
        assertEquals(text.length(), chunks.get(2).endOffset());
    }

    @Test
    @DisplayName("sections larger than targetSize are subdivided by sentences")
    void largeSectionIsSubdividedBySentences() {
        String big = "Sentence one. Sentence two. Sentence three. "
                + "Sentence four. Sentence five. Sentence six.";
        String text = "Intro\n\n" + big;
        List<HeadingInfo> headings = List.of(new HeadingInfo(1, "Intro", 0));

        List<Chunk> chunks = new StructureAwareChunker(60, headings).chunk(text);

        assertTrue(chunks.size() >= 2, "large section should split into multiple chunks");
        ChunkerSanityTests.assertIndicesSequential(chunks);
        ChunkerSanityTests.assertOffsetsConsistent(text, chunks);
    }

    @Test
    @DisplayName("a small section stays as a single chunk")
    void smallSectionStaysAsOneChunk() {
        String text = "Alpha\n\nBeta body text.";
        List<HeadingInfo> headings = List.of(
                new HeadingInfo(1, "Alpha", 0)
        );
        List<Chunk> chunks = new StructureAwareChunker(200, headings).chunk(text);
        assertEquals(1, chunks.size());
        assertEquals(text, chunks.get(0).text());
    }

    @Test
    @DisplayName("metadata carries the heading path")
    void metadataCarriesHeadingPath() {
        String text = "Intro\n\nSection A\n\nBody A.\n\nSub A1\n\nBody A1.";
        List<HeadingInfo> headings = List.of(
                new HeadingInfo(1, "Intro", 0),
                new HeadingInfo(2, "Section A", 7),
                new HeadingInfo(3, "Sub A1", 27)
        );

        List<Chunk> chunks = new StructureAwareChunker(200, headings).chunk(text);

        assertEquals("Intro", chunks.get(0).metadata().get(Chunk.HEADING_PATH));
        assertEquals("Intro / Section A",
                chunks.get(1).metadata().get(Chunk.HEADING_PATH));
        assertEquals("Intro / Section A / Sub A1",
                chunks.get(2).metadata().get(Chunk.HEADING_PATH));
    }
}
