package com.svenruppert.flow.views.module04;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.views.module01.LlmClient;
import com.svenruppert.flow.views.module02.VectorStore;
import com.svenruppert.flow.views.module03.Chunk;
import com.svenruppert.flow.views.module03.Chunker;
import com.svenruppert.flow.views.module03.Document;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Glues the pieces from modules 1, 2 and 3 into a single ingestion
 * step: take a {@link Document} (from m3), chunk it (m3), embed each
 * chunk via the {@link LlmClient} (m1), and register the result in
 * both the vector {@link VectorStore} (m2) and the {@link KeywordIndex}
 * so the hybrid retriever can see every chunk.
 *
 * <p>Chunk ids follow the pattern {@code <source-filename>::<chunk-index>}
 * so a later UI row can quote the originating file without a reverse
 * lookup through the registry.
 */
public final class IngestionPipeline implements HasLogger {

    private final LlmClient llmClient;
    private final String embeddingModel;
    private final Chunker chunker;
    private final VectorStore vectorStore;
    private final KeywordIndex keywordIndex;
    private final Map<String, Chunk> chunkRegistry = new LinkedHashMap<>();

    public IngestionPipeline(LlmClient llmClient,
                             String embeddingModel,
                             Chunker chunker,
                             VectorStore vectorStore,
                             KeywordIndex keywordIndex) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel");
        this.chunker = Objects.requireNonNull(chunker, "chunker");
        this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore");
        this.keywordIndex = Objects.requireNonNull(keywordIndex, "keywordIndex");
    }

    /**
     * Chunks the document, embeds each chunk through the LLM client and
     * registers it in both stores. Chunks whose embedding fails are
     * <em>skipped</em>: the keyword side can still find them later, but
     * a reader chose to gate ingestion on the embedder, not the other
     * way around. A warning is logged per skipped chunk.
     */
    public void ingest(Document document) throws IOException {
        Objects.requireNonNull(document, "document");
        String fileName = document.source().getFileName().toString();

        for (Chunk chunk : chunker.chunk(document.content())) {
            String id = fileName + "::" + chunk.index();
            Optional<float[]> vector = llmClient.embed(chunk.text(), embeddingModel);
            if (vector.isEmpty()) {
                logger().warn("Skipping chunk {} -- embedding failed", id);
                continue;
            }
            vectorStore.add(id, vector.get(), chunk.text());
            keywordIndex.add(id, chunk.text());
            chunkRegistry.put(id, chunk);
        }
    }

    /** All chunks ingested so far, in insertion order. */
    public Collection<Chunk> allChunks() {
        return chunkRegistry.values();
    }

    /** Id -> chunk lookup; the retrievers use this to rejoin hits. */
    public Map<String, Chunk> chunkRegistry() {
        return chunkRegistry;
    }

    /** Wipes both stores and the local registry. */
    public void clear() throws IOException {
        chunkRegistry.clear();
        vectorStore.clear();
        keywordIndex.clear();
    }
}
