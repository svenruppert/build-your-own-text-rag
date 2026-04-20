# Module 04 -- Retrieval

Four first-stage retrievers and one reranker over a shared corpus. The
point of the module is that retrieval is a deliberate design decision,
and that the architecture choice (bi-encoder vs. cross-encoder) is a
real one even when, as in this workshop, every implementation happens
to live on the same runtime (Ollama).

## What this module solves

- Four first-stage retrievers behind a single `Retriever` interface:
  `VectorRetriever`, `BM25Retriever`, `HybridRetriever` (with two
  fusion strategies: `ReciprocalRankFusion` and
  `WeightedScoreFusion`).
- One reranker behind a single `Reranker` interface:
  `LlmJudgeReranker` -- prompt a general instruction-tuned LLM with
  a 0-10 relevance question per candidate and read the score back.
- A composite `RerankingRetriever` that wires any `Retriever` to a
  `Reranker` so the first-stage-plus-rerank pipeline is one object.
- A `LuceneBM25KeywordIndex` that brings Lucene's BM25 scoring in with
  two jars and no query parser dependency.
- An `IngestionPipeline` that glues modules 1, 2 and 3 together --
  chunk, embed, register in both vector store and keyword index.
- A Vaadin lab view to actually run these combinations against your
  own uploads.

## Why no cross-encoder reranker

A cross-encoder reranker would, in principle, be the right tool for
the second stage: a small model trained specifically to read
`(query, passage)` pairs and emit a single relevance score. The
canonical Java path for that is ONNX Runtime with a model like
`bge-reranker-v2-m3`.

We do **not** ship a cross-encoder reranker in this module. Reason:
**Ollama does not expose `/api/rerank`**, and BGE cross-encoder models
running through Ollama only surface their embedding head, not the
classification head that would produce relevance scores. A
a "cross-encoder via Ollama" therefore has nothing to call. The
LLM-as-judge variant, with all its caveats, is the only reranker
this module can honestly demonstrate against a self-hosted Ollama.

The cross-encoder path is covered conceptually on the slides;
production code reaches it through ONNX Runtime, outside the
workshop's no-native-code rule.

## Why Lucene for BM25

Lucene is the canonical Java implementation of BM25 -- proven,
maintained, battle-tested. We pull in `lucene-core` and
`lucene-analysis-common` only: no query parser, no extras. The
`LuceneBM25KeywordIndex` tokenises queries through the same
`StandardAnalyzer` the ingestion used and assembles a simple OR of
`TermQuery`s, which is enough for the natural-language queries the
workshop throws at it and keeps the module's surface area small.

`BM25Similarity` is applied on both ends (writer config and searcher)
so the scores are consistent across ingest and retrieval.

## RRF in one line

```
score_i = sum over retrievers (1 / (rrfK + rank_i))
```

`rrfK` defaults to 60 (the original paper's value). RRF ignores the
scale of the underlying scores entirely -- it only looks at the rank
each retriever assigned. That is exactly why it works unchanged when
mixing BM25 (raw scores, unbounded) with cosine (in `[-1, 1]`): the
scales do not matter.

## Weighted-score in one line

```
score_i = w_v * normalise(v_i) + w_b * normalise(b_i)
```

Each retriever's batch is divided by its own max score so both sides
sit in `[0, 1]` before combination. If one retriever returns an empty
or all-zero batch, that side contributes zero; the fusion gracefully
degrades to the other.

## The reranker, in one paragraph

`LlmJudgeReranker` prompts a general instruction-tuned model
(`llama3.2` by default) with "On a scale of 0 to 10, how relevant is
this passage to the query?", then peels any `<think>...</think>`
block off the reply, parses the first number out of the remainder,
and divides by 10 so the resulting score lives in `[0, 1]`. **Not** a
true cross-encoder -- we are piggybacking on an LLM's language
ability for a scoring task it was not specifically trained on. Slower
and noisier than a proper reranker; reachable on every Ollama install
with no additional infrastructure.

When the judge is a reasoning model (deepseek-r1, qwen3-thinking,
gpt-oss) the chain of thought is captured -- both from inline
`<think>` tags and from Ollama's separate `thinking` field -- and
shown per candidate in the lab view's collapsible "Judge thinking"
panel.

## The Retrieval Lab view

Route `/Module04` (sidebar "Module 04"):

1. Upload up to 10 `.txt`/`.md` files, click **Ingest**.
   `FileDocumentLoader + SentenceChunker(400) + IngestionPipeline` run
   each file, feeding the vector store and the keyword index in lock-step.
2. Pick a retriever; the conditional parameters (rrfK, weights) appear
   underneath.
3. Optionally pick the LLM-as-judge reranker; a model dropdown and the
   first-stage-k field appear.
4. **Search**. The latency strip shows total wall-clock time; the grid
   below shows a source-label pill (coloured per retriever / reranker),
   chunk id, the first-stage score (when reranking is on), the final
   score, the heading path (when the chunker produced one), and a
   120-character preview of the chunk text.

Uploads are written to a per-session temp directory; that directory is
deleted on view detach. The keyword index is closed on detach as well.

## Tests

Under
[`src/test/java/com/svenruppert/flow/views/module04/`](../src/test/java/com/svenruppert/flow/views/module04/):

- `VectorRetrieverTest`, `BM25RetrieverTest`, `HybridRetrieverTest`
  (four cases including the three weight combinations),
  `LlmJudgeRerankerTest`, `IngestionPipelineTest`.
- `testutil/StubLlmClient` -- a lightweight `LlmClient` double with
  canned embed/generate responses.
- `testutil/TestChunks` -- chunk factory for deterministic offsets.
