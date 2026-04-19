package com.svenruppert.flow.views.module04;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.views.module01.LlmClient;
import com.svenruppert.flow.views.module02.SearchHit;
import com.svenruppert.flow.views.module02.VectorStore;
import com.svenruppert.flow.views.module03.Chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link Retriever} that embeds the query via the {@link LlmClient}
 * and delegates nearest-neighbour search to the {@link VectorStore}
 * (module 2).
 *
 * <p>Chunks are rejoined through the ingestion-time id registry so
 * this retriever can emit full {@link RetrievalHit}s carrying the
 * original chunk metadata (heading path, offsets) for downstream use.
 */
public final class VectorRetriever implements Retriever, HasLogger {

    private final LlmClient llmClient;
    private final String embeddingModel;
    private final VectorStore vectorStore;
    private final Map<String, Chunk> chunkRegistry;

    public VectorRetriever(LlmClient llmClient,
                           String embeddingModel,
                           VectorStore vectorStore,
                           Map<String, Chunk> chunkRegistry) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel");
        this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore");
        this.chunkRegistry = Objects.requireNonNull(chunkRegistry, "chunkRegistry");
    }

    @Override
    public List<RetrievalHit> retrieve(String query, int k) {
        Objects.requireNonNull(query, "query");
        if (k <= 0 || chunkRegistry.isEmpty()) return List.of();

        Optional<float[]> maybeVector = llmClient.embed(query, embeddingModel);
        if (maybeVector.isEmpty()) {
            logger().warn("Query embedding failed -- returning empty result set");
            return List.of();
        }
        List<SearchHit> hits = vectorStore.search(maybeVector.get(), k);

        List<RetrievalHit> out = new ArrayList<>(hits.size());
        for (SearchHit hit : hits) {
            Chunk chunk = chunkRegistry.get(hit.id());
            if (chunk == null) continue; // stale id, skip rather than fail
            out.add(new RetrievalHit(chunk, hit.score(), "vector"));
        }
        return List.copyOf(out);
    }
}
