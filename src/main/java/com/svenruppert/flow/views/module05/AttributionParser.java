package com.svenruppert.flow.views.module05;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities for finding and highlighting chunk citations of the form
 * {@code [Chunk N]} in a generated answer.
 *
 * <p>The prompt instructs the model to number chunks starting at 1;
 * {@link #parseReferences(String, int)} converts those 1-based numbers
 * to zero-based indices (to match Java collection conventions) and
 * drops anything that falls outside the expected range.
 */
public final class AttributionParser {

    private AttributionParser() {
    }

    /**
     * Matches {@code [Chunk N]} where {@code N} is a positive integer.
     * Tolerates interior whitespace ({@code [Chunk  3]}) because small
     * models sometimes produce it.
     */
    private static final Pattern CHUNK_REFERENCE = Pattern.compile("\\[Chunk\\s+(\\d+)\\]");

    /**
     * Returns the sorted, deduplicated, zero-based indices of chunks
     * cited in {@code text}. References outside {@code [0, totalChunks)}
     * are silently dropped.
     */
    public static List<Integer> parseReferences(String text, int totalChunks) {
        Objects.requireNonNull(text, "text");
        if (totalChunks <= 0) return List.of();
        Set<Integer> unique = new TreeSet<>();
        Matcher matcher = CHUNK_REFERENCE.matcher(text);
        while (matcher.find()) {
            int oneBased;
            try {
                oneBased = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                continue;
            }
            int zeroBased = oneBased - 1;
            if (zeroBased >= 0 && zeroBased < totalChunks) {
                unique.add(zeroBased);
            }
        }
        return List.copyOf(new ArrayList<>(unique));
    }

    /**
     * Wraps every {@code [Chunk N]} whose 1-based number is in
     * {@code wantedIndices} (interpreted as zero-based -- i.e. 0 means
     * the reference "[Chunk 1]") in a
     * {@code <mark class="chunk-ref" data-chunk="N">...</mark>} tag.
     * References outside the wanted set are left untouched.
     */
    public static String highlight(String text, Set<Integer> wantedIndices) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(wantedIndices, "wantedIndices");
        Matcher matcher = CHUNK_REFERENCE.matcher(text);
        StringBuilder out = new StringBuilder();
        int cursor = 0;
        while (matcher.find()) {
            int oneBased;
            try {
                oneBased = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                continue;
            }
            int zeroBased = oneBased - 1;
            out.append(text, cursor, matcher.start());
            if (wantedIndices.contains(zeroBased)) {
                out.append("<mark class=\"chunk-ref\" data-chunk=\"")
                        .append(oneBased).append("\">")
                        .append(matcher.group())
                        .append("</mark>");
            } else {
                out.append(matcher.group());
            }
            cursor = matcher.end();
        }
        out.append(text, cursor, text.length());
        return out.toString();
    }
}
