package com.svenruppert.flow.views.module03;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * A document that has already been read from disk and reduced to its
 * plain-text form. Carries the original {@link Path} and a small
 * open-ended metadata map for downstream consumers (content type,
 * parsed heading structure for Markdown, etc.).
 *
 * <p>Metadata is normalised: passing {@code null} at construction
 * yields an empty map, never {@code null}. The stored map is a
 * defensive copy so callers can safely mutate their own collections
 * afterwards.
 *
 * <p>Well-known metadata keys are exposed as constants so tests and
 * downstream code never need to repeat the magic strings.
 *
 * @param content  extracted plain text, non-{@code null}
 * @param source   the file this document was loaded from, non-{@code null}
 * @param metadata arbitrary structured metadata; may be {@code null} on
 *                 construction, empty otherwise
 */
public record Document(String content, Path source, Map<String, Object> metadata) {

    /** Metadata key: MIME-ish content type label, e.g. {@code "text/plain"}. */
    public static final String CONTENT_TYPE = "content-type";

    /** Metadata key: {@code List<HeadingInfo>} for Markdown documents. */
    public static final String HEADINGS = "headings";

    public Document {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(source, "source");
        metadata = (metadata == null) ? Map.of() : Map.copyOf(metadata);
    }
}
