package com.svenruppert.flow.views.module06;

import com.svenruppert.flow.views.module03.Document;
import com.svenruppert.flow.views.module04.IngestionPipeline;
import com.svenruppert.flow.views.module04.LuceneBM25KeywordIndex;
import com.svenruppert.flow.views.module05.GeneratedAnswer;
import com.svenruppert.flow.views.module05.RagPipeline;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Product-facing entry point for the five-module RAG stack.
 *
 * <p>Modules 1-5 intentionally expose a workshop-sized surface: every
 * knob is visible so a participant can feel which piece does what.
 * Module 6 turns that into a product by hiding the knobs and handing
 * the caller two verbs:
 * <ul>
 *   <li>{@link #ingest(String, String)} -- "add this text to the
 *       knowledge base and tell me what happened",</li>
 *   <li>{@link #ask(String, Consumer)} -- "answer this question,
 *       streaming the tokens as they come in".</li>
 * </ul>
 *
 * <p>All configuration lives in {@link ProductConfig} and is wired by
 * {@link RagSystemBuilder}; a caller never reaches into the individual
 * modules.
 *
 * <p>Not constructable directly: go through {@link #builder()}.
 */
public final class RagSystem implements AutoCloseable {

    private final IngestionPipeline ingestionPipeline;
    private final LuceneBM25KeywordIndex keywordIndex;
    private final RagPipeline ragPipeline;
    private final String generationModel;
    private final int retrievalK;

    RagSystem(IngestionPipeline ingestionPipeline,
              LuceneBM25KeywordIndex keywordIndex,
              RagPipeline ragPipeline,
              String generationModel,
              int retrievalK) {
        this.ingestionPipeline = Objects.requireNonNull(ingestionPipeline, "ingestionPipeline");
        this.keywordIndex = Objects.requireNonNull(keywordIndex, "keywordIndex");
        this.ragPipeline = Objects.requireNonNull(ragPipeline, "ragPipeline");
        this.generationModel = Objects.requireNonNull(generationModel, "generationModel");
        if (retrievalK <= 0) {
            throw new IllegalArgumentException("retrievalK must be > 0, got " + retrievalK);
        }
        this.retrievalK = retrievalK;
    }

    /** Start here: a fresh {@link RagSystemBuilder} with product defaults. */
    public static RagSystemBuilder builder() {
        return new RagSystemBuilder();
    }

    /**
     * Ingests {@code text} as a new source named {@code sourceName}.
     *
     * <p>Wraps the text in a synthetic {@link Document} (the pipeline
     * keys chunks by filename, so the source name must be unique per
     * logical document), runs it through the {@link IngestionPipeline},
     * and returns a summary of how many chunks ended up in the store.
     *
     * @throws IllegalStateException if the embedder or the stores fail
     */
    public IngestionResult ingest(String sourceName, String text) {
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(text, "text");
        int before = ingestionPipeline.chunkRegistry().size();
        Document document = new Document(text, Path.of(sourceName), null);
        try {
            ingestionPipeline.ingest(document);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Ingestion of '" + sourceName + "' failed: " + e.getMessage(), e);
        }
        int after = ingestionPipeline.chunkRegistry().size();
        return new IngestionResult(sourceName, after - before, after);
    }

    /**
     * Answers {@code query} using the configured retriever-generator-
     * grounding pipeline. Tokens are forwarded to {@code tokenSink}
     * as they arrive; the returned {@link GeneratedAnswer} carries the
     * assembled text plus the hits that grounded it.
     */
    public GeneratedAnswer ask(String query, Consumer<String> tokenSink) {
        return ask(query, tokenSink, null, null);
    }

    /**
     * Observability-rich variant of {@link #ask(String, Consumer)}.
     *
     * <p>The two extra sinks are what a product UI typically needs on
     * top of the answer stream:
     * <ul>
     *   <li>{@code thinkingSink} -- a reasoning model's thinking tokens
     *       land here; non-thinking models simply never call it. A
     *       {@code null} sink is a no-op.</li>
     *   <li>{@code stageListener} -- receives each phase transition
     *       (retrieval / generation / grounding), so a UI can show a
     *       step indicator or a progress bar that reflects the pipeline's
     *       current state rather than freezing while a long model call
     *       runs. A {@code null} listener is a no-op.</li>
     * </ul>
     */
    public GeneratedAnswer ask(String query,
                               Consumer<String> tokenSink,
                               Consumer<String> thinkingSink,
                               RagPipeline.StageListener stageListener) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(tokenSink, "tokenSink");
        return ragPipeline.ask(query, retrievalK, generationModel,
                tokenSink, thinkingSink, stageListener);
    }

    /**
     * Names of the ingested source documents, in ingestion order.
     * Derived from the {@code <filename>::<index>} convention that
     * {@link IngestionPipeline} uses for chunk ids. A
     * {@link LinkedHashSet} preserves the order so a UI list stays
     * stable across renders.
     */
    public List<String> listSources() {
        Map<String, ?> registry = ingestionPipeline.chunkRegistry();
        Set<String> sources = new LinkedHashSet<>();
        for (String id : registry.keySet()) {
            int sep = id.indexOf("::");
            sources.add(sep < 0 ? id : id.substring(0, sep));
        }
        return new ArrayList<>(sources);
    }

    /**
     * Number of distinct source documents that have been ingested.
     * Equivalent to {@code listSources().size()}.
     */
    public int documentCount() {
        return listSources().size();
    }

    /**
     * Removes every chunk belonging to {@code sourceName} from the
     * vector store, the keyword index and the chunk registry. No-op
     * for a name that was never ingested.
     *
     * @return the number of chunks actually removed
     */
    public int removeSource(String sourceName) {
        Objects.requireNonNull(sourceName, "sourceName");
        try {
            return ingestionPipeline.removeSource(sourceName);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not remove '" + sourceName + "': " + e.getMessage(), e);
        }
    }

    /**
     * Wipes the knowledge base: after this call the system has no
     * documents, no chunks, no vectors and no BM25 entries. The
     * {@link RagSystem} itself remains usable -- a subsequent
     * {@link #ingest(String, String)} repopulates it.
     */
    public void clearAll() {
        try {
            ingestionPipeline.clear();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not clear the knowledge base: " + e.getMessage(), e);
        }
    }

    /** Total number of chunks across all ingested sources. */
    public int chunkCount() {
        return ingestionPipeline.chunkRegistry().size();
    }

    /** Releases the Lucene resources held by the keyword index. */
    @Override
    public void close() throws IOException {
        keywordIndex.close();
    }
}
