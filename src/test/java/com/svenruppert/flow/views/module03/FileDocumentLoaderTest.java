package com.svenruppert.flow.views.module03;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileDocumentLoaderTest {

    private final FileDocumentLoader loader = new FileDocumentLoader();

    private static Path fixture(String name) throws URISyntaxException {
        URL url = FileDocumentLoaderTest.class.getResource("/documents/" + name);
        if (url == null) {
            throw new IllegalStateException("missing /documents/" + name + " on test classpath");
        }
        return Paths.get(url.toURI());
    }

    @Test
    @DisplayName(".txt fixtures load as text/plain with the file contents")
    void loadsPlainTextWithCorrectMetadata() throws Exception {
        Document doc = loader.load(fixture("simple.txt"));
        assertEquals("text/plain", doc.metadata().get(Document.CONTENT_TYPE));
        assertFalse(doc.metadata().containsKey(Document.HEADINGS),
                "plain-text documents must not carry a headings entry");
        assertTrue(doc.content().startsWith("Hello, world."));
    }

    @Test
    @DisplayName(".md fixtures expose extracted text and a heading list")
    void loadsMarkdownWithExtractedTextAndHeadings() throws Exception {
        Document doc = loader.load(fixture("simple.md"));
        assertEquals("text/markdown", doc.metadata().get(Document.CONTENT_TYPE));

        @SuppressWarnings("unchecked")
        List<HeadingInfo> headings = (List<HeadingInfo>) doc.metadata().get(Document.HEADINGS);
        assertEquals(3, headings.size(),
                "fixture has three headings: Introduction / Motivation / Details");
        assertEquals("Introduction", headings.get(0).title());
        assertEquals("Motivation", headings.get(1).title());
        assertEquals("Details", headings.get(2).title());

        // Fence markers and URLs must be gone; inner content preserved.
        assertFalse(doc.content().contains("```"));
        assertFalse(doc.content().contains("https://example.com"));
        assertTrue(doc.content().contains("System.out.println"));
        assertTrue(doc.content().contains("our website"));
    }

    @Test
    @DisplayName("unknown extensions fall back to plain text")
    void unknownExtensionFallsBackToPlainText(@TempDir Path tempDir) throws IOException {
        Path unusual = tempDir.resolve("notes.xyz");
        Files.writeString(unusual, "just text", StandardCharsets.UTF_8);
        Document doc = loader.load(unusual);
        assertEquals("text/plain", doc.metadata().get(Document.CONTENT_TYPE));
        assertEquals("just text", doc.content());
    }

    @Test
    @DisplayName("UTF-8 is handled correctly")
    void loadHandlesUtf8Content() throws Exception {
        Document doc = loader.load(fixture("simple.txt"));
        assertTrue(doc.content().contains("café"), "café must round-trip");
        assertTrue(doc.content().contains("naïve"), "naïve must round-trip");
        assertTrue(doc.content().contains("résumé"), "résumé must round-trip");
    }
}
