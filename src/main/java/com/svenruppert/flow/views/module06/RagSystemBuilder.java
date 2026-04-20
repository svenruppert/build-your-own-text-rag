package com.svenruppert.flow.views.module06;

import com.svenruppert.flow.views.module01.LlmClient;
import com.svenruppert.flow.views.module02.VectorStore;
import com.svenruppert.flow.views.module03.Chunker;
import com.svenruppert.flow.views.module04.BM25Retriever;
import com.svenruppert.flow.views.module04.FusionStrategy;
import com.svenruppert.flow.views.module04.HybridRetriever;
import com.svenruppert.flow.views.module04.IngestionPipeline;
import com.svenruppert.flow.views.module04.LlmJudgeReranker;
import com.svenruppert.flow.views.module04.LuceneBM25KeywordIndex;
import com.svenruppert.flow.views.module04.RerankingRetriever;
import com.svenruppert.flow.views.module04.Retriever;
import com.svenruppert.flow.views.module04.VectorRetriever;
import com.svenruppert.flow.views.module05.DefaultGenerator;
import com.svenruppert.flow.views.module05.DefaultGroundingChecker;
import com.svenruppert.flow.views.module05.Generator;
import com.svenruppert.flow.views.module05.GroundingChecker;
import com.svenruppert.flow.views.module05.PromptTemplate;
import com.svenruppert.flow.views.module05.RagPipeline;
import com.svenruppert.flow.views.module05.StreamingLlmApi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fluent builder that composes the five workshop modules into a single
 * {@link RagSystem}. Every setter returns {@code this} so a call site
 * reads top-to-bottom like a wiring diagram; every field carries a
 * Javadoc hint about which module the type originates in.
 *
 * <p>The builder is the didactic point of module 6. By spelling out
 * which piece comes from which module (m1 = LLM client, m2 = vector
 * store, m3 = chunker, m4 = retrievers + reranker, m5 = streaming
 * generator + prompt template + grounding check) it turns the
 * abstract "RAG pipeline" into a compositional map a participant can
 * point at on a slide.
 *
 * <h2>Required vs. optional</h2>
 * <ul>
 *   <li><strong>Required</strong>: {@code llmClient}, {@code vectorStore},
 *       {@code chunker}, {@code promptTemplate}, {@code streamingApi}.
 *       Missing any of these makes {@link #build()} throw
 *       {@link IllegalStateException} with a list of the missing
 *       fields -- so a participant who forgot one piece sees it all
 *       at once, not one at a time.</li>
 *   <li><strong>Optional</strong>: model names, retriever mode, fusion
 *       strategy, k values and feature toggles. Every default comes
 *       from {@link ProductConfig}.</li>
 * </ul>
 */
public final class RagSystemBuilder {

    /**
     * Which retrieval strategy the built system should use. Picked up
     * by {@link #build()} to wire either a single retriever or the
     * hybrid two-retriever fusion.
     */
    public enum RetrieverMode {
        /** Dense-only: embed the query, cosine-search the vector store. */
        VECTOR_ONLY,
        /** Keyword-only: tokenise the query, BM25-search the Lucene index. */
        BM25_ONLY,
        /** Both, merged via a {@link FusionStrategy}. The workshop default. */
        HYBRID
    }

    // ----- required fields (no defaults; build() validates) ---------

    /** Module 1: talks to Ollama for embeddings and (when grounding is on) scoring. */
    private LlmClient llmClient;

    /** Module 2: stores and searches the dense embeddings. */
    private VectorStore vectorStore;

    /** Module 3: splits a document into {@link com.svenruppert.flow.views.module03.Chunk}s. */
    private Chunker chunker;

    /** Module 5: shapes the prompt sent to the generator. */
    private PromptTemplate promptTemplate;

    /** Module 5: streaming backend for the answer generator. */
    private StreamingLlmApi streamingApi;

    // ----- optional fields (defaults from ProductConfig) ------------

    /** Module 1: model name used for embeddings. */
    private String embeddingModel = ProductConfig.DEFAULT_EMBEDDING_MODEL;

    /** Module 1: model name used for generation (and for the grounding check). */
    private String generationModel = ProductConfig.DEFAULT_GENERATION_MODEL;

    /** Module 4: which retrieval path to wire. */
    private RetrieverMode retrieverMode = RetrieverMode.HYBRID;

    /** Module 4: how to merge vector and BM25 results when hybrid is on. */
    private FusionStrategy fusionStrategy =
            new FusionStrategy.ReciprocalRankFusion(ProductConfig.RRF_K);

    /** Module 4: toggle the LLM-as-judge reranker on top of the retriever. */
    private boolean rerankingEnabled = ProductConfig.RERANKING_DEFAULT;

    /** Module 5: toggle the grounding post-check. */
    private boolean groundingCheckEnabled = ProductConfig.GROUNDING_CHECK_DEFAULT;

    /** Module 4/5: how many hits to hand the generator. */
    private int retrievalK = ProductConfig.RETRIEVAL_K;

    /** Module 4: first-stage candidate pool for hybrid retrieval. */
    private int hybridFirstStageK = ProductConfig.HYBRID_FIRST_STAGE_K;

    // ----- fluent setters -------------------------------------------

    public RagSystemBuilder llmClient(LlmClient llmClient) {
        this.llmClient = llmClient;
        return this;
    }

    public RagSystemBuilder vectorStore(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        return this;
    }

    public RagSystemBuilder chunker(Chunker chunker) {
        this.chunker = chunker;
        return this;
    }

    public RagSystemBuilder promptTemplate(PromptTemplate promptTemplate) {
        this.promptTemplate = promptTemplate;
        return this;
    }

    public RagSystemBuilder streamingApi(StreamingLlmApi streamingApi) {
        this.streamingApi = streamingApi;
        return this;
    }

    public RagSystemBuilder embeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
        return this;
    }

    public RagSystemBuilder generationModel(String generationModel) {
        this.generationModel = generationModel;
        return this;
    }

    public RagSystemBuilder retrieverMode(RetrieverMode retrieverMode) {
        this.retrieverMode = retrieverMode;
        return this;
    }

    public RagSystemBuilder fusionStrategy(FusionStrategy fusionStrategy) {
        this.fusionStrategy = fusionStrategy;
        return this;
    }

    public RagSystemBuilder withReranking(boolean rerankingEnabled) {
        this.rerankingEnabled = rerankingEnabled;
        return this;
    }

    public RagSystemBuilder withGroundingCheck(boolean groundingCheckEnabled) {
        this.groundingCheckEnabled = groundingCheckEnabled;
        return this;
    }

    public RagSystemBuilder retrievalK(int retrievalK) {
        this.retrievalK = retrievalK;
        return this;
    }

    public RagSystemBuilder hybridFirstStageK(int hybridFirstStageK) {
        this.hybridFirstStageK = hybridFirstStageK;
        return this;
    }

    // ----- build ----------------------------------------------------

    /**
     * Validates the required fields, wires every module into one
     * {@link RagSystem}, and returns it. Throws
     * {@link IllegalStateException} -- with a list of the missing field
     * names -- if any required field is still {@code null}.
     */
    public RagSystem build() {
        validate();
        LuceneBM25KeywordIndex keywordIndex;
        try {
            keywordIndex = new LuceneBM25KeywordIndex();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not open LuceneBM25KeywordIndex: " + e.getMessage(), e);
        }
        IngestionPipeline ingestionPipeline = new IngestionPipeline(
                llmClient, embeddingModel, chunker, vectorStore, keywordIndex);
        VectorRetriever vector = new VectorRetriever(
                llmClient, embeddingModel, vectorStore, ingestionPipeline.chunkRegistry());
        BM25Retriever bm25 = new BM25Retriever(keywordIndex, ingestionPipeline.chunkRegistry());
        Retriever base = switch (retrieverMode) {
            case VECTOR_ONLY -> vector;
            case BM25_ONLY -> bm25;
            case HYBRID -> new HybridRetriever(vector, bm25, fusionStrategy, hybridFirstStageK);
        };
        Retriever retriever = rerankingEnabled
                ? new RerankingRetriever(base,
                new LlmJudgeReranker(llmClient, generationModel),
                hybridFirstStageK)
                : base;
        Generator generator = new DefaultGenerator(streamingApi, promptTemplate);
        Optional<GroundingChecker> groundingChecker = groundingCheckEnabled
                ? Optional.of(new DefaultGroundingChecker(llmClient))
                : Optional.empty();
        RagPipeline ragPipeline = new RagPipeline(retriever, generator, groundingChecker);
        return new RagSystem(ingestionPipeline, keywordIndex, ragPipeline,
                generationModel, retrievalK);
    }

    private void validate() {
        List<String> missing = new ArrayList<>();
        if (llmClient == null) missing.add("llmClient");
        if (vectorStore == null) missing.add("vectorStore");
        if (chunker == null) missing.add("chunker");
        if (promptTemplate == null) missing.add("promptTemplate");
        if (streamingApi == null) missing.add("streamingApi");
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot build RagSystem -- missing required fields: " + missing);
        }
    }
}
