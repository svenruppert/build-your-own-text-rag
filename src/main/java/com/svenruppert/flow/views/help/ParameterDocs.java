package com.svenruppert.flow.views.help;

/**
 * Central catalogue of inline help entries for every user-facing
 * control across the workshop's Lab views. One {@link HelpEntry}
 * constant per parameter; the HTML body is authored by hand so a
 * trainer can edit wording without touching the views.
 *
 * <p>Naming: {@code MXX_} prefix for module-specific entries; plain
 * upper-case names are reserved for entries shared across modules
 * (none yet -- every view has its own context).
 *
 * <p>Tone is direct and practical: one sentence of purpose, the valid
 * range, the shipped default, and one hint about what happens at the
 * extremes. British English throughout.
 */
public final class ParameterDocs {

  private ParameterDocs() {
    // utility -- not instantiable
  }

  // =================================================================
  // Module 1 -- Talking to Ollama
  // =================================================================

  public static final HelpEntry M1_MODEL = new HelpEntry(
      "Generation model",
      """
          <p><strong>Purpose.</strong> Which local Ollama model answers
          the chat prompt. Every request posts to <code>/api/generate</code>
          against this model; swapping it is the single biggest lever on
          quality, latency and VRAM use.</p>
          <p><strong>Values.</strong> Whatever <code>ollama list</code>
          reports. Default falls back to <code>llama3.2</code> if the
          list call fails.</p>
          <p><strong>Hint.</strong> Small instruct models (3-8B) are
          fast and adequate for short chat; larger or reasoning models
          (<code>qwen3</code>, <code>deepseek-r1</code>) give better
          answers but can take tens of seconds per reply on a laptop.</p>
          """);

  public static final HelpEntry M1_MARKDOWN = new HelpEntry(
      "Render Markdown",
      """
          <p><strong>Purpose.</strong> Switches assistant replies
          between a Markdown-rendered view and the raw text the model
          emitted. LLMs reach for Markdown by default (bullets, code
          fences, pipe tables), so rendering usually reads better.</p>
          <p><strong>Values.</strong> On (rendered) or off (plain text).
          Default on.</p>
          <p><strong>Hint.</strong> Turn off to inspect the exact
          bytes the model produced, including fence markers and heading
          characters -- useful when debugging formatting oddities. The
          whole history re-renders in place when toggled.</p>
          """);

  public static final HelpEntry M1_TEMPERATURE = new HelpEntry(
      "Temperature",
      """
          <p><strong>Purpose.</strong> Controls how deterministic the
          model's sampling is. Low values pin the model to its highest-
          probability token; high values let it roam.</p>
          <p><strong>Range.</strong> 0.0 to 1.0 typical (Ollama accepts
          up to 2.0). Default 0.7 for chat.</p>
          <p><strong>Hint.</strong> For factual Q&amp;A or code, keep
          below 0.3 so the model commits to one answer. For creative
          drafting, 0.7-0.9 gives variety. Above 1.0 the output often
          degrades into incoherent text.</p>
          """);

  // =================================================================
  // Module 2 -- Vector Store Lab
  // =================================================================

  public static final HelpEntry M2_EMBEDDING_MODEL = new HelpEntry(
      "Embedding model",
      """
          <p><strong>Purpose.</strong> Which Ollama model turns text
          into a vector. All stored vectors must come from the same
          model; changing this after adding entries makes the corpus
          inconsistent.</p>
          <p><strong>Values.</strong> Any embedding-capable model
          returned by Ollama. Defaults to <code>nomic-embed-text</code>
          when available.</p>
          <p><strong>Hint.</strong> <code>nomic-embed-text</code> (768
          dims) is a strong default for English. <code>mxbai-embed-large</code>
          (1024 dims) gives slightly better retrieval at more memory
          cost. Clear the store before switching, or added and query
          vectors will no longer compare meaningfully.</p>
          """);

  public static final HelpEntry M2_DIMENSIONS = new HelpEntry(
      "Vector dimensions",
      """
          <p><strong>Purpose.</strong> The length of each embedding
          vector, fixed by the embedding model. Shown for context --
          it is not user-editable.</p>
          <p><strong>Values.</strong> Typically 384, 768 or 1024.
          <code>nomic-embed-text</code> emits 768, <code>mxbai-embed-large</code>
          emits 1024.</p>
          <p><strong>Hint.</strong> Higher dimensions mean more
          expressive vectors but also more memory and slower search.
          The stored corpus's dimensionality is locked the first time
          you add a vector; mixing dimensions later is rejected.</p>
          """);

  public static final HelpEntry M2_HNSW_M = new HelpEntry(
      "HNSW M (graph connectivity)",
      """
          <p><strong>Purpose.</strong> The maximum number of out-edges
          per node in the HNSW graph the JVector store builds. Higher
          <code>M</code> gives a denser graph and better recall, at
          the cost of more memory and slower index build.</p>
          <p><strong>Range.</strong> 8 to 64 practical. Default 16.</p>
          <p><strong>Hint.</strong> 16 is a good baseline. Raise to 32
          only if recall is measurably poor on your corpus; doubling
          <code>M</code> roughly doubles index memory. Rebuild the
          store after changing this.</p>
          """);

  public static final HelpEntry M2_HNSW_EF = new HelpEntry(
      "HNSW efConstruction",
      """
          <p><strong>Purpose.</strong> The size of the dynamic candidate
          list the index keeps while inserting a new node. Larger values
          make the graph more accurate; they do not affect query time
          directly.</p>
          <p><strong>Range.</strong> 40 to 400 practical. Default 100.</p>
          <p><strong>Hint.</strong> Raise this before raising
          <code>M</code> if recall is poor -- it is cheaper on memory
          and only slows indexing, not search. Changes take effect on
          the next rebuild.</p>
          """);

  public static final HelpEntry M2_TOP_K = new HelpEntry(
      "Top-k (search results)",
      """
          <p><strong>Purpose.</strong> How many nearest-neighbour hits
          the store returns for the current query vector.</p>
          <p><strong>Range.</strong> 1 to 50 practical. Default 5.</p>
          <p><strong>Hint.</strong> Small <code>top-k</code> is easier
          to scan visually. Raise it to explore the long tail of a
          corpus; latency is almost unaffected because HNSW's work
          scales with <code>efSearch</code>, not with <code>k</code>.</p>
          """);

  public static final HelpEntry M2_ADD_ID = new HelpEntry(
      "Id (entry identifier)",
      """
          <p><strong>Purpose.</strong> The unique label that identifies
          this entry inside the store. Shown in the hits grid beside the
          score, and used as the key when an entry is updated or removed.
          The stores reject a second add with the same id.</p>
          <p><strong>Values.</strong> Any non-empty string. Leave blank
          to auto-generate <code>auto-&lt;uuid8&gt;</code>.</p>
          <p><strong>Hint.</strong> Give a meaningful id
          (<code>doc-42</code>, <code>faq-ollama</code>) when you want
          to spot a specific entry in search results or replace it later;
          leave blank during exploration, the auto-id is plenty.</p>
          """);

  public static final HelpEntry M2_ADD_PAYLOAD = new HelpEntry(
      "Payload (display text)",
      """
          <p><strong>Purpose.</strong> The human-readable text shown in
          the hits grid for this entry. It is stored alongside the
          vector but does <em>not</em> go through the embedding model --
          the embedded text is the separate &quot;Text to embed&quot;
          field.</p>
          <p><strong>Values.</strong> Any text. Defaults to the embedded
          text when left blank.</p>
          <p><strong>Hint.</strong> For quick labs, leave blank -- the
          same text is both embedded and displayed. Override when the
          representation you want to search for differs from what the
          user should see: embed a short query-form phrase, for example,
          but display the full passage, a URL, or a title.</p>
          """);

  public static final HelpEntry M2_ACTIVE_STORE = new HelpEntry(
      "Active store (toggle)",
      """
          <p><strong>Purpose.</strong> Which store backs the hits grid.
          Both stores always receive every add and every query in
          lock-step; this toggle only changes what the grid displays.</p>
          <p><strong>Values.</strong> <code>InMemoryVectorStore</code>
          (linear scan) or <code>EclipseStoreJVectorStore</code>
          (persistent vectors + HNSW index).</p>
          <p><strong>Hint.</strong> Switch back and forth after a few
          hundred adds to watch the JVector store pull ahead on query
          latency -- the timing strip records both regardless of the
          toggle's position.</p>
          """);

  // =================================================================
  // Module 3 -- Chunking Lab
  // =================================================================

  public static final HelpEntry M3_CHUNKER = new HelpEntry(
      "Chunker choice",
      """
          <p><strong>Purpose.</strong> Which algorithm carves the
          document into retrievable units. Each trades off boundary
          fidelity against simplicity.</p>
          <p><strong>Options.</strong></p>
          <ul>
            <li><strong>Fixed size</strong> -- hard character count, cuts
              through words and sentences. Fastest, least faithful.</li>
            <li><strong>Overlapping</strong> -- fixed size plus a trailing
              overlap so boundary information is recoverable.</li>
            <li><strong>Sentence</strong> -- respects sentence ends, pads
              towards the target size. The workshop default.</li>
            <li><strong>Structure-aware</strong> -- aligns chunks with
              Markdown headings and carries the heading path along.</li>
          </ul>
          <p><strong>Hint.</strong> Start with <code>Sentence</code>
          for prose; switch to <code>Structure-aware</code> when
          headings carry real meaning (manuals, wikis).</p>
          """);

  public static final HelpEntry M3_CHUNK_SIZE = new HelpEntry(
      "Chunk target size (characters)",
      """
          <p><strong>Purpose.</strong> How many characters each chunk
          aims for. The sentence- and structure-aware chunkers treat
          this as a soft target and round to the next boundary; the
          fixed-size chunker treats it as a hard cut.</p>
          <p><strong>Range.</strong> 100 to 2000 practical. Defaults
          per tab: 200 (fixed), 200 (overlap), 300 (sentence), 400
          (structure).</p>
          <p><strong>Hint.</strong> Small chunks (around 200) keep the
          LLM context tight but can split answers across pieces. Large
          chunks (over 800) carry more context but push out competing
          chunks from the top-k. 300-500 is a reasonable default for
          English prose.</p>
          """);

  public static final HelpEntry M3_OVERLAP = new HelpEntry(
      "Overlap (characters)",
      """
          <p><strong>Purpose.</strong> How many characters each chunk
          repeats from the tail of the previous one. Ensures a fact
          that straddles a boundary ends up fully inside at least one
          chunk.</p>
          <p><strong>Range.</strong> 0 to half the chunk size.
          Default 40.</p>
          <p><strong>Hint.</strong> Around 10-20% of the chunk size
          is typical. Zero makes boundaries brittle; very high values
          waste storage and push duplicates into retrieval results.</p>
          """);

  // =================================================================
  // Module 4 -- Retrieval Lab
  // =================================================================

  public static final HelpEntry M4_RETRIEVER_MODE = new HelpEntry(
      "Retriever mode",
      """
          <p><strong>Purpose.</strong> Which index answers the query.
          Vector is semantic; BM25 is lexical; hybrid fuses the two.</p>
          <p><strong>Options.</strong></p>
          <ul>
            <li><strong>Vector</strong> -- nearest-neighbour over embeddings.
              Strong on paraphrase, weak on rare proper nouns.</li>
            <li><strong>BM25</strong> -- term-frequency scoring. Strong on
              exact terms and acronyms, blind to synonyms.</li>
            <li><strong>Hybrid (RRF)</strong> -- fuses both ranked lists
              by reciprocal rank.</li>
            <li><strong>Hybrid (weighted)</strong> -- linear blend of
              normalised scores.</li>
          </ul>
          <p><strong>Hint.</strong> Hybrid is the safer default for
          mixed queries; pure vector wins when the corpus is well-
          structured prose; pure BM25 wins on code or logs.</p>
          """);

  public static final HelpEntry M4_TOP_K = new HelpEntry(
      "Top-k (number of chunks returned)",
      """
          <p><strong>Purpose.</strong> How many chunks the retriever
          returns for each query. These chunks form the evidence the
          language model sees when generating an answer.</p>
          <p><strong>Range.</strong> 1 to 20 practical. Default 5.</p>
          <p><strong>Hint.</strong> Small <code>top-k</code> keeps the
          prompt short and focused; the model sees fewer distractions
          but may miss context in longer answers. Large <code>top-k</code>
          broadens evidence but costs context-window tokens and can
          dilute the best chunk's influence. For single-document
          questions 3 to 5 is usually enough; for questions that span
          multiple documents, 7 to 10 is a reasonable start.</p>
          """);

  public static final HelpEntry M4_FIRST_STAGE_K = new HelpEntry(
      "First-stage k",
      """
          <p><strong>Purpose.</strong> How many candidates the retriever
          hands to the fusion or reranker step before cutting down to
          <code>top-k</code>. Larger first-stage pool gives fusion and
          rerankers more to work with.</p>
          <p><strong>Range.</strong> At least <code>top-k</code>; 10 to
          50 practical. Default 20 when the LLM-as-judge reranker is
          active.</p>
          <p><strong>Hint.</strong> Raise this if the reranker keeps
          picking the wrong chunks -- the first stage may simply be
          missing better candidates. Each extra candidate adds one
          judge-LLM call, so the latency cost is linear.</p>
          """);

  public static final HelpEntry M4_FUSION_STRATEGY = new HelpEntry(
      "Fusion strategy",
      """
          <p><strong>Purpose.</strong> How the hybrid retriever
          combines the vector and BM25 ranked lists into a single
          ordering.</p>
          <p><strong>Options.</strong></p>
          <ul>
            <li><strong>Reciprocal Rank Fusion (RRF)</strong> -- score-
              free, uses only each list's rank. Robust across score
              scales. Default.</li>
            <li><strong>Weighted Score</strong> -- normalises each
              list's scores to <code>[0,1]</code> and takes a weighted
              sum. Tunable but sensitive to scale.</li>
          </ul>
          <p><strong>Hint.</strong> Stick with RRF unless you have a
          reason to tune weights -- raw BM25 and cosine scores live on
          different scales, which weighted fusion has to compensate for.</p>
          """);

  public static final HelpEntry M4_RRF_K = new HelpEntry(
      "RRF smoothing constant (k)",
      """
          <p><strong>Purpose.</strong> The constant added to each
          rank in the reciprocal-rank-fusion formula
          <code>1 / (k + rank)</code>. Larger <code>k</code> flattens
          the contribution curve so later ranks still influence the
          blend.</p>
          <p><strong>Range.</strong> 30 to 100 practical. Default 60
          (the value from the original RRF paper).</p>
          <p><strong>Hint.</strong> Rarely worth tuning. Below 30 the
          top-1 on each list dominates almost completely; above 100
          the two lists blur into a near-average.</p>
          """);

  public static final HelpEntry M4_VECTOR_WEIGHT = new HelpEntry(
      "Vector weight (weighted fusion)",
      """
          <p><strong>Purpose.</strong> The weight applied to the
          vector retriever's score in the weighted-score fusion
          formula. The BM25 weight is the complement.</p>
          <p><strong>Range.</strong> 0.0 to 1.0. Default 0.6.</p>
          <p><strong>Hint.</strong> Raise when the corpus is prose-
          heavy and paraphrase-sensitive; lower when users search by
          exact keywords or code tokens. 0.0 collapses to pure BM25,
          1.0 to pure vector.</p>
          """);

  public static final HelpEntry M4_BM25_WEIGHT = new HelpEntry(
      "BM25 weight (weighted fusion)",
      """
          <p><strong>Purpose.</strong> The weight applied to the BM25
          retriever's score in weighted-score fusion. Paired with the
          vector weight -- the two are typically kept summing to 1.0
          by convention, although the code does not enforce that.</p>
          <p><strong>Range.</strong> 0.0 to 1.0. Default 0.4.</p>
          <p><strong>Hint.</strong> Raise for keyword-heavy corpora
          (code, logs, technical references); lower for natural-
          language prose where synonyms and paraphrase dominate.</p>
          """);

  public static final HelpEntry M4_RERANKER = new HelpEntry(
      "Reranker",
      """
          <p><strong>Purpose.</strong> Reorders the first-stage hits
          with a second, usually heavier model to push the most
          relevant chunks to the top.</p>
          <p><strong>Options.</strong></p>
          <ul>
            <li><strong>None</strong> -- first-stage scores alone decide
              the order. Default.</li>
            <li><strong>LLM-as-judge (Ollama)</strong> -- asks the chosen
              model to score each candidate; latency grows linearly
              with <code>first-stage k</code>.</li>
          </ul>
          <p><strong>Hint.</strong> Turn the reranker on only when
          first-stage ranking is visibly wrong; it is one LLM call per
          candidate, so 20 candidates against a reasoning model can
          take minutes on a laptop.</p>
          """);

  public static final HelpEntry M4_RERANK_INPUT_K = new HelpEntry(
      "Rerank input count",
      """
          <p><strong>Purpose.</strong> How many first-stage candidates
          feed the reranker. The reranker then keeps the best
          <code>top-k</code> after scoring.</p>
          <p><strong>Range.</strong> At least <code>top-k</code>; 10
          to 50 practical. Default 20.</p>
          <p><strong>Hint.</strong> Larger means the reranker has
          more chance to promote a buried gem, at a linear latency
          cost. 15-25 is a sweet spot for an LLM judge on a laptop.</p>
          """);

  public static final HelpEntry M4_JUDGE_MODEL = new HelpEntry(
      "Judge model",
      """
          <p><strong>Purpose.</strong> Which Ollama model acts as the
          LLM judge for reranking. Each first-stage candidate triggers
          one call against this model, so both quality and latency
          scale with the choice.</p>
          <p><strong>Values.</strong> Any instruct or reasoning model
          pulled into Ollama.</p>
          <p><strong>Hint.</strong> A small instruct model (3-8B) is
          usually enough; reasoning models (<code>qwen3-thinking</code>,
          <code>deepseek-r1</code>) give noticeably better ordering but
          multiply latency. Use a smaller judge than the generation
          model.</p>
          """);

  // =================================================================
  // Module 5 -- Ask Lab (generation)
  // =================================================================

  public static final HelpEntry M5_GENERATION_MODEL = new HelpEntry(
      "Generation model",
      """
          <p><strong>Purpose.</strong> Which Ollama model writes the
          grounded answer from the retrieved chunks.</p>
          <p><strong>Values.</strong> Any chat-capable model pulled
          into Ollama; the list is fetched from
          <code>/api/tags</code>. Default prefers <code>llama</code>
          or <code>qwen</code> family models.</p>
          <p><strong>Hint.</strong> Small instruct models answer
          quickly and stay on-context if the prompt is tight. Reasoning
          models emit thinking tokens into the side panel and produce
          better answers on multi-chunk questions, at a latency cost
          of seconds to minutes.</p>
          """);

  public static final HelpEntry M5_PROMPT_TEMPLATE = new HelpEntry(
      "Prompt template (strict refusal)",
      """
          <p><strong>Purpose.</strong> Which grounded-prompt wrapper
          goes around the retrieved chunks. The strict template tells
          the model to refuse when the context does not support an
          answer.</p>
          <p><strong>Options.</strong></p>
          <ul>
            <li><strong>off</strong> -- <code>SimpleGroundedPromptTemplate</code>.
              Model may fall back to parametric knowledge.</li>
            <li><strong>on</strong> -- <code>StrictRefusalPromptTemplate</code>.
              Model is instructed to answer &quot;I don't know&quot;
              when the chunks do not contain the answer.</li>
          </ul>
          <p><strong>Hint.</strong> Turn on for compliance-sensitive
          demos; leave off for exploratory chatting. A strict template
          increases refusal rate but reduces hallucination.</p>
          """);

  public static final HelpEntry M5_RETRIEVAL_K = new HelpEntry(
      "Retrieval k (for generation)",
      """
          <p><strong>Purpose.</strong> How many chunks the retriever
          hands to the generator as evidence. The same knob as
          Module 4's <code>top-k</code>, but here the trade-off
          emphasises prompt size.</p>
          <p><strong>Range.</strong> 1 to 15 practical. Default 5.</p>
          <p><strong>Hint.</strong> Every extra chunk adds roughly its
          size in tokens to the prompt. On a laptop with a 4k context
          and 400-char chunks, 5 to 8 chunks is the comfortable range;
          larger starts to crowd out the answer.</p>
          """);

  public static final HelpEntry M5_GROUNDING_CHECK = new HelpEntry(
      "Grounding check",
      """
          <p><strong>Purpose.</strong> A second, post-generation LLM
          pass that inspects whether the answer is actually supported
          by the retrieved chunks, and tags the result grounded /
          partial / not-grounded / unknown.</p>
          <p><strong>Values.</strong> On or off. Off by default in
          Module 5.</p>
          <p><strong>Hint.</strong> Turn on to expose silent
          hallucination; it is a separate LLM call against the same
          model, so it roughly doubles the ask latency. In production
          the check often runs against a small, cheap model rather
          than the main generator.</p>
          """);

  public static final HelpEntry M5_RETRIEVER_MODE = new HelpEntry(
      "Retriever mode",
      """
          <p><strong>Purpose.</strong> Which retriever feeds the
          generator with evidence.</p>
          <p><strong>Options.</strong></p>
          <ul>
            <li><strong>Vector</strong> -- semantic nearest-neighbour.</li>
            <li><strong>BM25</strong> -- lexical term-frequency scoring.</li>
            <li><strong>Hybrid (RRF)</strong> -- reciprocal-rank fusion
              of the two. Default.</li>
          </ul>
          <p><strong>Hint.</strong> Hybrid is the safest baseline for
          mixed corpora; swap to pure vector on clean prose and to
          pure BM25 on keyword-heavy technical text to feel each
          retriever's bias.</p>
          """);

  // =================================================================
  // Module 6 -- Product
  // =================================================================

  public static final HelpEntry M6_UPLOAD = new HelpEntry(
      "Document upload",
      """
          <p><strong>Purpose.</strong> Drop a <code>.txt</code> or
          <code>.md</code> file; the product ingests it on arrival --
          chunks it, embeds every chunk, persists the vectors. Unlike
          the lab views there is no separate &quot;Ingest&quot; step.</p>
          <p><strong>Values.</strong> Up to 20 text or Markdown files
          per session; each served embedded with
          <code>nomic-embed-text-v2-moe</code>.</p>
          <p><strong>Hint.</strong> Embedding is the slow part --
          expect a second or more per chunk on a laptop. The progress
          bar shows an indeterminate pulse while the embed pass runs
          and turns green on completion. Existing documents can be
          removed through the chip strip below the upload.</p>
          """);
}
