package com.svenruppert.flow.views.module04;

import java.io.IOException;
import java.util.List;

/**
 * A pluggable keyword (as opposed to vector) index. The default
 * implementation used in this module is {@link LuceneBM25KeywordIndex};
 * the interface is narrow so a future module could drop in a different
 * scoring model without touching the retrievers above.
 */
public interface KeywordIndex extends AutoCloseable {

    /**
     * Indexes a single entry. Calling {@code add} with an id that
     * already exists <em>appends</em> a second document: this index
     * does not deduplicate. The caller is expected to use unique ids
     * (the {@link IngestionPipeline} does).
     */
    void add(String id, String text) throws IOException;

    /**
     * Returns the top-{@code k} ids by the underlying relevance score
     * (BM25, in the default implementation) together with the score.
     */
    List<KeywordSearchResult> search(String query, int k) throws IOException;

    /** Current number of indexed documents. */
    int size() throws IOException;

    /** Empties the index without releasing any native/IO resources. */
    void clear() throws IOException;

    /**
     * Removes a single document by id. Absent ids are silently ignored.
     *
     * <p>Default: {@link UnsupportedOperationException}, so an index
     * that cannot remove individually still satisfies the interface.
     * The workshop's Lucene backend overrides this.
     */
    default void remove(String id) throws IOException {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " does not support per-id removal");
    }

    @Override
    void close() throws IOException;
}
