package com.svenruppert.flow.views.module05;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttributionParserTest {

    @Test
    @DisplayName("extracts every citation found in the text")
    void parseReferences_extractsAllCitations() {
        String text = "Paris is the capital [Chunk 2]. It lies on the Seine [Chunk 3].";
        List<Integer> refs = AttributionParser.parseReferences(text, 5);
        assertEquals(List.of(1, 2), refs);
    }

    @Test
    @DisplayName("deduplicates repeated citations")
    void parseReferences_deduplicatesCitations() {
        String text = "See [Chunk 1] and also [Chunk 1], and [Chunk 3]; again [Chunk 3].";
        List<Integer> refs = AttributionParser.parseReferences(text, 5);
        assertEquals(List.of(0, 2), refs);
    }

    @Test
    @DisplayName("drops references outside [1, totalChunks]")
    void parseReferences_dropsOutOfRangeIndices() {
        // totalChunks = 3 -> valid 1..3, reject 0 and 4
        String text = "[Chunk 0] no; [Chunk 1] yes; [Chunk 4] no; [Chunk 3] yes.";
        List<Integer> refs = AttributionParser.parseReferences(text, 3);
        assertEquals(List.of(0, 2), refs);
    }

    @Test
    @DisplayName("returns the indices sorted and zero-based")
    void parseReferences_returnsSortedZeroBasedIndices() {
        String text = "[Chunk 5] then [Chunk 1] then [Chunk 3]";
        List<Integer> refs = AttributionParser.parseReferences(text, 10);
        assertEquals(List.of(0, 2, 4), refs);
    }

    @Test
    @DisplayName("highlight wraps every valid reference in a <mark> tag")
    void highlight_wrapsEveryValidReferenceOnce() {
        String text = "See [Chunk 1] and [Chunk 2].";
        String out = AttributionParser.highlight(text, Set.of(0, 1));
        assertTrue(out.contains("<mark class=\"chunk-ref\" data-chunk=\"1\">[Chunk 1]</mark>"));
        assertTrue(out.contains("<mark class=\"chunk-ref\" data-chunk=\"2\">[Chunk 2]</mark>"));
    }

    @Test
    @DisplayName("highlight leaves references outside the wanted set alone")
    void highlight_leavesInvalidReferencesAlone() {
        String text = "See [Chunk 1] and [Chunk 2].";
        String out = AttributionParser.highlight(text, Set.of(0)); // only Chunk 1 wanted
        assertTrue(out.contains("<mark class=\"chunk-ref\" data-chunk=\"1\">[Chunk 1]</mark>"));
        assertTrue(out.contains("[Chunk 2]"));
        assertTrue(!out.contains("data-chunk=\"2\""));
    }
}
