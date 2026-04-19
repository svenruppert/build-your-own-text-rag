package com.svenruppert.flow.views.module04;

import java.util.Objects;

/**
 * A single hit from a {@link KeywordIndex}: the stored id and the
 * index-specific relevance score (for BM25, the raw BM25 score, not
 * normalised).
 */
public record KeywordSearchResult(String id, double score) {

    public KeywordSearchResult {
        Objects.requireNonNull(id, "id");
    }
}
