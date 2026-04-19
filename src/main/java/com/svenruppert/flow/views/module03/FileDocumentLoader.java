package com.svenruppert.flow.views.module03;

import com.svenruppert.dependencies.core.logger.HasLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Filesystem {@link DocumentLoader}: dispatches on file extension and
 * falls back to UTF-8 plain text for anything it does not recognise.
 *
 * <ul>
 *   <li>{@code .md}, {@code .markdown} -> parsed via
 *       {@link MarkdownTextExtractor}; heading hierarchy retained as
 *       {@link Document#HEADINGS} metadata.</li>
 *   <li>Everything else (including explicit {@code .txt}) -> read as
 *       UTF-8 plain text.</li>
 * </ul>
 */
public final class FileDocumentLoader implements DocumentLoader, HasLogger {

    private final MarkdownTextExtractor markdownExtractor = new MarkdownTextExtractor();

    @Override
    public Document load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        String raw = Files.readString(path, StandardCharsets.UTF_8);
        String extension = extensionOf(path);

        return switch (extension) {
            case "md", "markdown" -> loadMarkdown(path, raw);
            default -> loadPlainText(path, raw);
        };
    }

    private Document loadPlainText(Path path, String raw) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(Document.CONTENT_TYPE, "text/plain");
        logger().debug("Loaded {} as text/plain ({} chars)", path, raw.length());
        return new Document(raw, path, metadata);
    }

    private Document loadMarkdown(Path path, String raw) {
        MarkdownTextExtractor.ExtractionResult result = markdownExtractor.extract(raw);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(Document.CONTENT_TYPE, "text/markdown");
        metadata.put(Document.HEADINGS, result.headings());
        logger().debug("Loaded {} as text/markdown ({} chars, {} headings)",
                path, result.plainText().length(), result.headings().size());
        return new Document(result.plainText(), path, metadata);
    }

    private static String extensionOf(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
