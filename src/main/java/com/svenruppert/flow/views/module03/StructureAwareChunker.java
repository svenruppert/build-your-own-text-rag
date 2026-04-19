package com.svenruppert.flow.views.module03;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Structure-aware chunker -- the bonus track of module 3. Chunk
 * boundaries snap to heading transitions, so each chunk lives within
 * exactly one section of the source document. A section that is already
 * small enough becomes a single chunk; a long section is further split
 * by a {@link SentenceChunker} configured to the same {@code targetSize}.
 *
 * <p>Every emitted chunk carries its heading path under
 * {@link Chunk#HEADING_PATH} -- for example
 * {@code "Introduction / Motivation"}. Chunks produced from text before
 * the first heading (a document "prologue") receive an empty path.
 *
 * <p>The chunker is stateless after construction. It receives the list
 * of {@link HeadingInfo} entries up front because those are already
 * available on the {@link Document} built by {@link FileDocumentLoader}
 * -- re-parsing the Markdown here would duplicate work.
 */
public final class StructureAwareChunker implements Chunker {

    private final int targetSize;
    private final List<HeadingInfo> headings;
    private final SentenceChunker sentenceChunker;

    public StructureAwareChunker(int targetSize, List<HeadingInfo> headings) {
        if (targetSize <= 0) {
            throw new IllegalArgumentException(
                    "targetSize must be > 0, got " + targetSize);
        }
        Objects.requireNonNull(headings, "headings");
        this.targetSize = targetSize;
        // Defensive copy + sort by offset so section boundaries are
        // deterministic regardless of caller ordering.
        List<HeadingInfo> sorted = new ArrayList<>(headings);
        sorted.sort((a, b) -> Integer.compare(a.offset(), b.offset()));
        this.headings = List.copyOf(sorted);
        this.sentenceChunker = new SentenceChunker(targetSize);
    }

    @Override
    public List<Chunk> chunk(String text) {
        Objects.requireNonNull(text, "text");
        if (text.isEmpty()) return List.of();

        List<Section> sections = buildSections(text);
        List<Chunk> out = new ArrayList<>();
        int runningIndex = 0;

        for (Section section : sections) {
            Map<String, Object> metadata = metadataFor(section.headingPath());

            if (section.length() <= targetSize) {
                out.add(new Chunk(
                        runningIndex++,
                        text.substring(section.start(), section.end()),
                        section.start(),
                        section.end(),
                        metadata));
            } else {
                String slice = text.substring(section.start(), section.end());
                for (Chunk sub : sentenceChunker.chunk(slice)) {
                    int globalStart = section.start() + sub.startOffset();
                    int globalEnd = section.start() + sub.endOffset();
                    out.add(new Chunk(
                            runningIndex++,
                            sub.text(),
                            globalStart,
                            globalEnd,
                            metadata));
                }
            }
        }
        return List.copyOf(out);
    }

    /**
     * Walks the headings in order and partitions the text into sections.
     * A "prologue" section is emitted if there is text before the first
     * heading; the last section always ends at {@code text.length()}.
     */
    private List<Section> buildSections(String text) {
        List<Section> sections = new ArrayList<>();

        // Optional prologue: text before the first heading (empty path).
        int firstHeadingOffset = headings.isEmpty() ? text.length() : headings.get(0).offset();
        if (firstHeadingOffset > 0) {
            sections.add(new Section(0, firstHeadingOffset, ""));
        }

        // Section per heading: title + body up to the next heading.
        Deque<HeadingInfo> stack = new ArrayDeque<>();
        for (int i = 0; i < headings.size(); i++) {
            HeadingInfo heading = headings.get(i);
            while (!stack.isEmpty() && stack.peekLast().level() >= heading.level()) {
                stack.pollLast();
            }
            stack.addLast(heading);

            int sectionStart = heading.offset();
            int sectionEnd = (i + 1 < headings.size())
                    ? headings.get(i + 1).offset()
                    : text.length();
            sections.add(new Section(sectionStart, sectionEnd, pathOf(stack)));
        }

        // A completely empty headings list still needs to produce the
        // single prologue covering the whole text.
        if (sections.isEmpty()) {
            sections.add(new Section(0, text.length(), ""));
        }
        // Drop zero-length sections (e.g. adjacent headings with no body).
        sections.removeIf(s -> s.length() == 0);
        return sections;
    }

    private static String pathOf(Deque<HeadingInfo> stack) {
        StringBuilder sb = new StringBuilder();
        for (HeadingInfo h : stack) {
            if (!sb.isEmpty()) sb.append(" / ");
            sb.append(h.title());
        }
        return sb.toString();
    }

    private static Map<String, Object> metadataFor(String headingPath) {
        if (headingPath.isEmpty()) return Map.of();
        Map<String, Object> m = new HashMap<>();
        m.put(Chunk.HEADING_PATH, headingPath);
        return m;
    }

    /** Internal section record: offset range plus the heading path that owns it. */
    private record Section(int start, int end, String headingPath) {
        int length() {
            return end - start;
        }
    }
}
