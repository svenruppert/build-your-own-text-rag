package com.svenruppert.flow.views.help;

/**
 * Central catalogue of inline help entries for every user-facing
 * control across the workshop's Lab views. One {@link HelpEntry}
 * constant per parameter; the HTML body lives in locale-specific
 * files under {@code src/main/resources/help/{locale}/{file}.html}
 * and is loaded at runtime by {@link HelpLoader}.
 *
 * <p>Naming: {@code MXX_} prefix for module-specific entries; plain
 * upper-case names are reserved for entries shared across modules
 * (none yet -- every view has its own context).
 */
public final class ParameterDocs {

  private ParameterDocs() {
    // utility -- not instantiable
  }

  // =================================================================
  // Module 1 -- Talking to Ollama
  // =================================================================

  public static final HelpEntry M1_MODEL       = new HelpEntry("m1.help.model.title",       "m1_model");
  public static final HelpEntry M1_MARKDOWN    = new HelpEntry("m1.help.markdown.title",    "m1_markdown");
  public static final HelpEntry M1_TEMPERATURE = new HelpEntry("m1.help.temperature.title", "m1_temperature");

  // =================================================================
  // Module 2 -- Vector Store Lab
  // =================================================================

  public static final HelpEntry M2_EMBEDDING_MODEL = new HelpEntry("m2.help.embedding.model.title", "m2_embedding_model");
  public static final HelpEntry M2_DIMENSIONS      = new HelpEntry("m2.help.dimensions.title",      "m2_dimensions");
  public static final HelpEntry M2_HNSW_M          = new HelpEntry("m2.help.hnsw.m.title",          "m2_hnsw_m");
  public static final HelpEntry M2_HNSW_EF         = new HelpEntry("m2.help.hnsw.ef.title",         "m2_hnsw_ef");
  public static final HelpEntry M2_TOP_K           = new HelpEntry("m2.help.top.k.title",           "m2_top_k");
  public static final HelpEntry M2_ADD_ID          = new HelpEntry("m2.help.add.id.title",          "m2_add_id");
  public static final HelpEntry M2_ADD_PAYLOAD     = new HelpEntry("m2.help.add.payload.title",     "m2_add_payload");
  public static final HelpEntry M2_ACTIVE_STORE    = new HelpEntry("m2.help.active.store.title",    "m2_active_store");

  // =================================================================
  // Module 3 -- Chunking Lab
  // =================================================================

  public static final HelpEntry M3_CHUNKER    = new HelpEntry("m3.help.chunker.title",     "m3_chunker");
  public static final HelpEntry M3_CHUNK_SIZE = new HelpEntry("m3.help.chunk.size.title",  "m3_chunk_size");
  public static final HelpEntry M3_OVERLAP    = new HelpEntry("m3.help.overlap.title",     "m3_overlap");

  // =================================================================
  // Module 4 -- Retrieval Lab
  // =================================================================

  public static final HelpEntry M4_RETRIEVER_MODE  = new HelpEntry("m4.help.retriever.mode.title",  "m4_retriever_mode");
  public static final HelpEntry M4_TOP_K           = new HelpEntry("m4.help.top.k.title",           "m4_top_k");
  public static final HelpEntry M4_FIRST_STAGE_K   = new HelpEntry("m4.help.first.stage.k.title",   "m4_first_stage_k");
  public static final HelpEntry M4_FUSION_STRATEGY = new HelpEntry("m4.help.fusion.strategy.title", "m4_fusion_strategy");
  public static final HelpEntry M4_RRF_K           = new HelpEntry("m4.help.rrf.k.title",           "m4_rrf_k");
  public static final HelpEntry M4_VECTOR_WEIGHT   = new HelpEntry("m4.help.vector.weight.title",   "m4_vector_weight");
  public static final HelpEntry M4_BM25_WEIGHT     = new HelpEntry("m4.help.bm25.weight.title",     "m4_bm25_weight");
  public static final HelpEntry M4_RERANKER        = new HelpEntry("m4.help.reranker.title",        "m4_reranker");
  public static final HelpEntry M4_RERANK_INPUT_K  = new HelpEntry("m4.help.rerank.input.k.title",  "m4_rerank_input_k");
  public static final HelpEntry M4_JUDGE_MODEL     = new HelpEntry("m4.help.judge.model.title",     "m4_judge_model");

  // =================================================================
  // Module 5 -- Ask Lab (generation)
  // =================================================================

  public static final HelpEntry M5_GENERATION_MODEL = new HelpEntry("m5.help.generation.model.title", "m5_generation_model");
  public static final HelpEntry M5_PROMPT_TEMPLATE  = new HelpEntry("m5.help.prompt.template.title",  "m5_prompt_template");
  public static final HelpEntry M5_RETRIEVAL_K      = new HelpEntry("m5.help.retrieval.k.title",      "m5_retrieval_k");
  public static final HelpEntry M5_GROUNDING_CHECK  = new HelpEntry("m5.help.grounding.check.title",  "m5_grounding_check");
  public static final HelpEntry M5_RETRIEVER_MODE   = new HelpEntry("m5.help.retriever.mode.title",   "m5_retriever_mode");

  // =================================================================
  // Module 6 -- Product
  // =================================================================

  public static final HelpEntry M6_UPLOAD = new HelpEntry("m6.help.upload.title", "m6_upload");
}
