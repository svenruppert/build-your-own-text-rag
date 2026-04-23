package com.svenruppert.flow.views.module04;

import com.svenruppert.flow.views.module01.LlmClient;
import com.svenruppert.flow.views.module02.DefaultSimilarity;
import com.svenruppert.flow.views.module02.InMemoryVectorStore;
import com.svenruppert.flow.views.module03.SentenceChunker;

import java.io.IOException;

/**
 * Session-scoped bundle of retrieval infrastructure shared by Modules
 * 4 and 5: an {@link InMemoryVectorStore}, a
 * {@link LuceneBM25KeywordIndex}, and an {@link IngestionPipeline}
 * wiring both back to the same chunk registry.
 *
 * <p>Before this helper each view re-implemented the same four-line
 * init in {@code onAttach} and the same close-on-detach in
 * {@code onDetach}; the retriever factory methods
 * ({@link #vectorRetriever()}, {@link #bm25Retriever()},
 * {@link #hybridRetriever(FusionStrategy, int)}) likewise repeated
 * identical plumbing. The lab owns the lifecycle and the shared
 * retriever wiring so the view code can focus on UI.
 *
 * <p>Close is idempotent and may throw -- Lucene's index close is
 * the only I/O here. Views call
 * {@code if (lab != null) lab.close()} in {@code onDetach}.
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 * // onAttach
 * this.lab = RetrievalLab.create(llmClient, EMBEDDING_MODEL);
 *
 * // buildRetriever:
 * return switch (choice) {
 *   case VECTOR -> lab.vectorRetriever();
 *   case BM25   -> lab.bm25Retriever();
 *   case HYBRID -> lab.hybridRetriever(
 *       new FusionStrategy.ReciprocalRankFusion(60.0), 10);
 * };
 *
 * // onDetach
 * if (lab != null) lab.close();
 * }</pre>
 */
public final class RetrievalLab implements AutoCloseable {

  private static final int DEFAULT_CHUNK_SIZE = 400;

  private final LlmClient llmClient;
  private final String embeddingModel;
  private final InMemoryVectorStore vectorStore;
  private final LuceneBM25KeywordIndex keywordIndex;
  private final IngestionPipeline pipeline;

  private RetrievalLab(LlmClient llmClient,
                       String embeddingModel,
                       InMemoryVectorStore vectorStore,
                       LuceneBM25KeywordIndex keywordIndex,
                       IngestionPipeline pipeline) {
    this.llmClient = llmClient;
    this.embeddingModel = embeddingModel;
    this.vectorStore = vectorStore;
    this.keywordIndex = keywordIndex;
    this.pipeline = pipeline;
  }

  /**
   * Creates a fresh lab. Opens a Lucene index on disk (Lucene's
   * constructor is the only source of {@link IOException} in the
   * init chain); the vector store and pipeline are in-memory.
   */
  public static RetrievalLab create(LlmClient llmClient,
                                    String embeddingModel) throws IOException {
    InMemoryVectorStore vs = new InMemoryVectorStore(new DefaultSimilarity());
    LuceneBM25KeywordIndex kw = new LuceneBM25KeywordIndex();
    IngestionPipeline pipeline = new IngestionPipeline(
        llmClient, embeddingModel, new SentenceChunker(DEFAULT_CHUNK_SIZE), vs, kw);
    return new RetrievalLab(llmClient, embeddingModel, vs, kw, pipeline);
  }

  public IngestionPipeline pipeline() {
    return pipeline;
  }

  public InMemoryVectorStore vectorStore() {
    return vectorStore;
  }

  public LuceneBM25KeywordIndex keywordIndex() {
    return keywordIndex;
  }

  /** New vector retriever over the lab's store and chunk registry. */
  public VectorRetriever vectorRetriever() {
    return new VectorRetriever(
        llmClient, embeddingModel, vectorStore, pipeline.chunkRegistry());
  }

  /** New BM25 retriever over the lab's keyword index and chunk registry. */
  public BM25Retriever bm25Retriever() {
    return new BM25Retriever(keywordIndex, pipeline.chunkRegistry());
  }

  /**
   * New hybrid retriever combining the lab's vector + BM25 retrievers
   * through the given fusion strategy, retrieving up to
   * {@code candidateK} candidates from each side.
   */
  public HybridRetriever hybridRetriever(FusionStrategy fusion, int candidateK) {
    return new HybridRetriever(vectorRetriever(), bm25Retriever(), fusion, candidateK);
  }

  @Override
  public void close() throws IOException {
    keywordIndex.close();
  }
}
