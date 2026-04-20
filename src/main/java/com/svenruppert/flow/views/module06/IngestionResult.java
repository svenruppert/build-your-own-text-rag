package com.svenruppert.flow.views.module06;

import java.util.Objects;

/**
 * Summary of a single {@link RagSystem#ingest} call.
 *
 * <p>The lab-style ingestion pipeline in module 4 returns {@code void}
 * because it is driven from the UI, one file at a time, with progress
 * shown separately. The product-facing surface of module 6 wants a
 * small structured return so a calling app can log "added N chunks
 * from source X" without re-querying the registry.
 *
 * @param sourceName        logical name of the source that was ingested
 * @param chunkCount        number of chunks successfully registered
 *                          (chunks whose embedding failed are not
 *                          counted)
 * @param totalChunksInStore the system-wide chunk count after this
 *                          ingestion
 */
public record IngestionResult(String sourceName, int chunkCount, int totalChunksInStore) {

    public IngestionResult {
        Objects.requireNonNull(sourceName, "sourceName");
        if (chunkCount < 0) {
            throw new IllegalArgumentException("chunkCount must be >= 0, got " + chunkCount);
        }
        if (totalChunksInStore < 0) {
            throw new IllegalArgumentException(
                    "totalChunksInStore must be >= 0, got " + totalChunksInStore);
        }
    }
}
