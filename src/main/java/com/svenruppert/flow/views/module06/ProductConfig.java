package com.svenruppert.flow.views.module06;

/**
 * Hard-wired production defaults for the Module 6 demo system.
 *
 * <p>Module 6 is not a lab -- it is the "product" framing of the RAG
 * stack built up in modules 1-5. A product has opinions: it does not
 * ask the user which chunker, which retriever, which prompt template.
 * Those choices live here, one constant per decision, with a short
 * rationale attached.
 *
 * <p>Non-instantiable: accessed through the public constants only.
 */
final class ProductConfig {

    /**
     * Embedding model. {@code nomic-embed-text} is the workshop's
     * default embedder -- small, fast, and available on every Ollama
     * install after a single pull.
     */
//    public static final String DEFAULT_EMBEDDING_MODEL = "nomic-embed-text";
    public static final String DEFAULT_EMBEDDING_MODEL = "nomic-embed-text-v2-moe";

    /**
     * Generation model. {@code llama3.2} is the smallest instruction
     * model most laptops can run at interactive speed; swappable for
     * anything Ollama serves.
     */
    public static final String DEFAULT_GENERATION_MODEL = "gemma4:e4b";

    /**
     * Target chunk size in characters for the {@code SentenceChunker}.
     * 400 characters is the module-3 default -- fits the context
     * windows of small models without starving retrieval.
     */
    public static final int CHUNK_TARGET_SIZE = 400;

    /**
     * Final number of hits shown to the generator. Five is the sweet
     * spot: enough context to ground an answer, few enough to fit
     * below even a 2k-token context window.
     */
    public static final int RETRIEVAL_K = 5;

    /**
     * First-stage candidate pool size for hybrid retrieval. Twice the
     * final {@code k} gives the fusion strategy room to re-order
     * without truncating too aggressively before fusion runs.
     */
    public static final int HYBRID_FIRST_STAGE_K = 10;

    /**
     * RRF constant. 60 is the original paper's value; it dampens the
     * top-rank advantage enough that one retriever cannot dominate
     * the fusion.
     */
    public static final double RRF_K = 60.0;

    /**
     * Grounding check on by default: in a product, "answer is plainly
     * unsupported" is worth flagging even at the cost of a second
     * round-trip.
     */
    public static final boolean GROUNDING_CHECK_DEFAULT = true;

    /**
     * Reranking off by default: the LLM-as-judge reranker is slow,
     * especially on reasoning models, and the first-stage hybrid
     * retriever already delivers reasonable precision for the
     * workshop corpus.
     */
    public static final boolean RERANKING_DEFAULT = false;

    private ProductConfig() {
        throw new UnsupportedOperationException("ProductConfig is not instantiable");
    }
}
