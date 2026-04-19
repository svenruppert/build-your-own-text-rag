package com.svenruppert.flow.views.module04;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.views.module03.Chunk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@link Retriever} driven by the keyword (BM25) index. Rejoins hit
 * ids against the chunk registry the same way {@link VectorRetriever}
 * does, so downstream code sees a uniform {@link RetrievalHit} stream
 * regardless of the retrieval modality.
 */
public final class BM25Retriever implements Retriever, HasLogger {

    private final KeywordIndex keywordIndex;
    private final Map<String, Chunk> chunkRegistry;

    public BM25Retriever(KeywordIndex keywordIndex, Map<String, Chunk> chunkRegistry) {
        this.keywordIndex = Objects.requireNonNull(keywordIndex, "keywordIndex");
        this.chunkRegistry = Objects.requireNonNull(chunkRegistry, "chunkRegistry");
    }

    @Override
    public List<RetrievalHit> retrieve(String query, int k) {
        Objects.requireNonNull(query, "query");
        if (k <= 0) return List.of();
        try {
            List<KeywordSearchResult> hits = keywordIndex.search(query, k);
            List<RetrievalHit> out = new ArrayList<>(hits.size());
            for (KeywordSearchResult hit : hits) {
                Chunk chunk = chunkRegistry.get(hit.id());
                if (chunk == null) continue;
                out.add(new RetrievalHit(chunk, hit.score(), "bm25"));
            }
            return List.copyOf(out);
        } catch (IOException e) {
            logger().warn("BM25 retrieval failed: {}", e.getMessage());
            return List.of();
        }
    }
}
