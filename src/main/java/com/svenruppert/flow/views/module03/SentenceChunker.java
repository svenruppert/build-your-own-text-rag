package com.svenruppert.flow.views.module03;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Sentence-aware chunker: respects sentence boundaries and packs
 * consecutive sentences into a chunk until the next sentence would push
 * the chunk past {@code targetSize} characters. The running chunk is
 * then closed and a new one begun.
 *
 * <p>Sentence recognition uses {@link BreakIterator#getSentenceInstance}
 * with {@code Locale.ENGLISH}. A fresh instance is created per
 * {@link #chunk(String)} call: {@code BreakIterator} is not thread-safe
 * and the chunker as a whole must be.
 *
 * <p>If a single sentence already exceeds {@code targetSize}, it is
 * emitted as its own (oversized) chunk rather than split. That keeps
 * sentence integrity -- the didactic point of sentence-aware chunking --
 * at the cost of occasional oversized chunks. Callers that care about
 * absolute size limits should clamp upstream (shorter paragraphs) or
 * downstream (a follow-up size guard).
 */
public final class SentenceChunker implements Chunker {

    private final int targetSize;

    public SentenceChunker(int targetSize) {
        if (targetSize <= 0) {
            throw new IllegalArgumentException(
                    "targetSize must be > 0, got " + targetSize);
        }
        this.targetSize = targetSize;
    }

    @Override
    public List<Chunk> chunk(String text) {
        Objects.requireNonNull(text, "text");
        if (text.isEmpty()) return List.of();

        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.ENGLISH);
        iterator.setText(text);

        List<Chunk> chunks = new ArrayList<>();
        int index = 0;
        int chunkStart = -1;
        int chunkEnd = -1;

        int sentenceStart = iterator.first();
        for (int sentenceEnd = iterator.next();
             sentenceEnd != BreakIterator.DONE;
             sentenceEnd = iterator.next()) {

            int sentenceLength = sentenceEnd - sentenceStart;
            if (chunkStart < 0) {
                // Start of a new chunk -- admit the sentence unconditionally,
                // even if it is already longer than targetSize.
                chunkStart = sentenceStart;
                chunkEnd = sentenceEnd;
            } else if ((chunkEnd - chunkStart) + sentenceLength <= targetSize) {
                // The sentence still fits; extend the current chunk.
                chunkEnd = sentenceEnd;
            } else {
                // Close the current chunk and start a new one.
                chunks.add(new Chunk(index++,
                        text.substring(chunkStart, chunkEnd), chunkStart, chunkEnd));
                chunkStart = sentenceStart;
                chunkEnd = sentenceEnd;
            }
            sentenceStart = sentenceEnd;
        }
        if (chunkStart >= 0 && chunkEnd > chunkStart) {
            chunks.add(new Chunk(index,
                    text.substring(chunkStart, chunkEnd), chunkStart, chunkEnd));
        }
        return List.copyOf(chunks);
    }
}
