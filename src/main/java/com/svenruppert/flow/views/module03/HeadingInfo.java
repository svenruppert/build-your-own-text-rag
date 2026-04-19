package com.svenruppert.flow.views.module03;

import java.util.Objects;

/**
 * A single heading as observed in a Markdown document, mapped onto the
 * offsets of the extracted plain text.
 *
 * <p>The {@code offset} is the character position at which the heading's
 * title appears in the extracted plain text, i.e. it can be used to
 * drive structure-aware chunking later on.
 *
 * @param level  heading level, {@code 1} to {@code 6}
 * @param title  heading title with inline Markdown syntax stripped
 * @param offset character position of the heading title in the extracted
 *               plain text, {@code >= 0}
 */
public record HeadingInfo(int level, String title, int offset) {

    public HeadingInfo {
        if (level < 1 || level > 6) {
            throw new IllegalArgumentException(
                    "heading level must be in [1, 6], got " + level);
        }
        Objects.requireNonNull(title, "title");
        if (offset < 0) {
            throw new IllegalArgumentException(
                    "offset must be >= 0, got " + offset);
        }
    }
}
