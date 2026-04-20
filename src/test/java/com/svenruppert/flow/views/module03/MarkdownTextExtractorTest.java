package com.svenruppert.flow.views.module03;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownTextExtractorTest {

    private final MarkdownTextExtractor extractor = new MarkdownTextExtractor();

    @Test
    @DisplayName("heading markers are stripped; titles survive")
    void extractsPlainTextFromHeadings() {
        String md = "# Title\n\n## Subtitle\n\nBody.";
        String out = extractor.extract(md).plainText();
        assertFalse(out.contains("#"), "# markers must be stripped");
        assertTrue(out.contains("Title"));
        assertTrue(out.contains("Subtitle"));
        assertTrue(out.contains("Body."));
    }

    @Test
    @DisplayName("heading hierarchy is preserved as metadata with offsets")
    void preservesHeadingHierarchyAsMetadata() {
        String md = "# Alpha\n\n## Beta\n\n### Gamma";
        MarkdownTextExtractor.ExtractionResult result = extractor.extract(md);
        List<HeadingInfo> headings = result.headings();

        assertEquals(3, headings.size());
        assertEquals(1, headings.get(0).level());
        assertEquals("Alpha", headings.get(0).title());
        assertEquals(2, headings.get(1).level());
        assertEquals("Beta", headings.get(1).title());
        assertEquals(3, headings.get(2).level());
        assertEquals("Gamma", headings.get(2).title());

        // Offsets line up with the extracted plain text.
        String text = result.plainText();
        for (HeadingInfo h : headings) {
            assertTrue(text.regionMatches(h.offset(), h.title(), 0, h.title().length()),
                    "heading '" + h.title() + "' missing at offset " + h.offset());
        }
    }

    @Test
    @DisplayName("fenced code block content survives untouched")
    void extractsCodeBlockContentUnchanged() {
        String md = "Before.\n\n```java\nSystem.out.println(\"hi\");\n```\n\nAfter.";
        String out = extractor.extract(md).plainText();
        assertTrue(out.contains("System.out.println(\"hi\");"),
                "code block content must be preserved");
        assertFalse(out.contains("```"), "fence markers must be stripped");
    }

    @Test
    @DisplayName("bullet list items become separate lines, bullets dropped")
    void extractsListItemsAsSeparateLines() {
        String md = "- one\n- two\n- three\n";
        String out = extractor.extract(md).plainText();
        assertFalse(out.contains("- "), "bullet markers must be dropped");
        String[] lines = out.split("\\R");
        // Expect "one", "two", "three" as distinct non-empty lines.
        long nonEmpty = List.of(lines).stream().filter(s -> !s.isBlank()).count();
        assertEquals(3, nonEmpty);
    }

    @Test
    @DisplayName("link URLs are dropped; link text survives")
    void dropsLinkUrlsKeepsLinkText() {
        String md = "See [the website](https://example.com) for details.";
        String out = extractor.extract(md).plainText();
        assertTrue(out.contains("the website"));
        assertFalse(out.contains("https://example.com"),
                "link URL must be dropped from plain text");
    }

    @Test
    @DisplayName("emphasis markers are dropped; inner text survives")
    void dropsEmphasisMarkersKeepsText() {
        String md = "An *italic* and a **bold** word.";
        String out = extractor.extract(md).plainText();
        assertTrue(out.contains("italic"));
        assertTrue(out.contains("bold"));
        assertFalse(out.contains("*"));
        assertFalse(out.contains("**"));
    }
}
