# Glossary

Twenty-six concepts and four tools that recur throughout the workshop.
Use the search to jump to a term; click any cross-reference to follow it.

## Concepts

### Attribution {#attribution}

The practice of making a generated answer point back to the chunks
that support each claim. In this workshop, attribution is encoded in
the [prompt template](#prompt-template) by labelling chunks `[Chunk 1]`,
`[Chunk 2]`, and so on, and instructing the language model to cite
those labels as it writes. After generation, an `AttributionParser`
extracts the cited labels and the UI highlights them inline. A cited
answer is a verifiable answer; without attribution, the reader cannot
distinguish between [grounded](#grounding) text and a confident
[hallucination](#hallucination).

See also: [Grounding](#grounding), [Prompt template](#prompt-template),
[Refusal](#refusal).

### BM25 {#bm25}

A keyword-based ranking function that scores documents by term
frequency and inverse document frequency. BM25 is the workhorse of
classical information retrieval: it rewards passages where the query
terms appear often, and discounts terms that appear in many documents
(because common terms tell you little about relevance). It is strong
on exact-term matches and rare identifiers — class names, acronyms,
file names — and weak on conceptual paraphrase. Pair it with a
[vector store](#vector-store) inside [hybrid retrieval](#hybrid-retrieval)
to get both signals.

See also: [Hybrid retrieval](#hybrid-retrieval), [RRF](#rrf),
[Retriever](#retriever).

### Chunk {#chunk}

A single passage of text extracted from a source document during
[chunking](#chunking). A chunk carries the text itself plus metadata:
which document it came from, where in that document it lives, and any
heading path from a structure-aware chunker. Chunks are the unit that
the [vector store](#vector-store) indexes, the [retriever](#retriever)
returns, and the [generator](#generator) sees as evidence. The
quality of a RAG system is decided by chunk quality more than by any
other single factor.

See also: [Chunking](#chunking), [Heading path](#heading-path),
[Vector store](#vector-store).

### Chunking {#chunking}

The process of splitting a document into smaller passages
([chunks](#chunking)) that can be embedded and retrieved
independently. Chunk boundaries are a design decision, not a neutral
pre-processing step: a fixed-size chunker cuts through sentences and
words, a sentence chunker respects sentence boundaries, and a
structure-aware chunker snaps to headings. The choice of strategy and
target size shapes everything downstream — what the
[retriever](#retriever) finds, what the [generator](#generator) sees,
and how attribution looks in the final answer.

See also: [Chunk](#chunk), [Heading path](#heading-path),
[Ingestion](#ingestion).

### Context window {#context-window}

The total number of tokens a language model can consider in a single
call. The window covers prompt, retrieved chunks, the user's query,
and the generated answer combined. A model with a 4,000-token window
runs out of room quickly when [top-k](#top-k) is large or chunks are
long. Choosing top-k is in part a budget decision: how much of the
window do you spend on evidence versus reserving for the answer? In
practice, oversized contexts also dilute attention — the model attends
less to each individual chunk when many are present.

See also: [Prompt template](#prompt-template), [Top-k](#top-k).

### Cross-encoder {#cross-encoder}

A reranking model that scores a query-document pair directly through a
classification head. The query and a candidate document are
concatenated, fed through a transformer, and a single relevance score
emerges. Cross-encoders are typically more accurate than
[LLM-as-judge](#llm-as-judge) reranking and an order of magnitude
faster, because they are small dedicated models with a trained
classification head rather than general-purpose language models
reasoning their way to a score. The BGE family from BAAI is the
best-known open-source choice. Cross-encoders cannot be served through
[Ollama](#ollama) at present — Ollama exposes only the embedding layer,
not the classification head — so production systems usually run them
in a Python sidecar.

See also: [LLM-as-judge](#llm-as-judge), [Reranker](#reranker).

### Embedding {#embedding}

A dense numerical vector representation of a text passage. An embedding
model maps any input text to a fixed-length vector — typically a few
hundred to a few thousand floating-point numbers — such that two texts
with similar meanings produce vectors close to each other in vector
space. Embeddings are how a [vector store](#vector-store) becomes
searchable by meaning rather than by exact words: a query embedding
near the embedding of a chunk indicates topical or semantic similarity
even when no shared words exist. The workshop uses
[Ollama](#ollama)-hosted embedding models such as `nomic-embed-text`
and `bge-m3`.

See also: [HNSW](#hnsw), [Vector store](#vector-store).

### Generator {#generator}

The component that turns a query plus a list of retrieved
[chunks](#chunk) into a final answer, using a language model. In this
workshop the generator is an interface with a single method that
takes the query, the chunks, the model name, and a token sink, and
returns a `GeneratedAnswer`. The default implementation streams tokens
from [Ollama](#ollama) and accumulates them into the final text. The
generator is responsible for refusal detection and reports the cited
chunks; it is not responsible for retrieval or for grounding-checking
— those live in adjacent components.

See also: [Prompt template](#prompt-template), [Streaming](#streaming),
[RAG](#rag).

### Grounding {#grounding}

The requirement that every claim in a generated answer is supported
by at least one [chunk](#chunk) the model was shown. A grounded
answer can be verified by reading the cited chunks; an ungrounded
answer asks the reader to trust the model. Without grounding, a
language model reaches into its training data and invents
plausible-sounding content — see [hallucination](#hallucination). The
workshop enforces grounding through the [prompt template](#prompt-template)
and verifies it post-hoc with a grounding-check pass that asks a
second LLM call to judge whether the answer is supported by the chunks.

See also: [Attribution](#attribution), [Hallucination](#hallucination),
[Refusal](#refusal).

### Hallucination {#hallucination}

A confident, fluent, and entirely fabricated statement from a language
model. Hallucinations occur when the model is asked something its
training data does not support, and it produces a plausible-sounding
answer anyway. RAG mitigates hallucinations by [grounding](#grounding)
generation in retrieved chunks — but only if the [prompt template](#prompt-template)
is strict and the model honours it. The single most important demo in
the workshop's Module 5 shows a hallucination disappearing when a
weak prompt template is replaced with a strict one, and the answer
shifts from a fabrication to an honest [refusal](#refusal).

See also: [Grounding](#grounding), [Refusal](#refusal).

### Heading path {#heading-path}

The breadcrumb of headings that lead to a [chunk](#chunk) inside its
source document, recorded as metadata by a structure-aware chunker.
A chunk extracted from inside the section *Reasons for the choice of
technology > EclipseStore as a persistence solution* carries that
exact path. Heading paths help the [retriever](#retriever) and the
human reviewer locate a chunk in its document context, and they make
attribution richer than just "from page 3".

See also: [Chunk](#chunk), [Chunking](#chunking).

### HNSW {#hnsw}

Hierarchical Navigable Small World — a graph-based index for
approximate nearest-neighbour search over high-dimensional vectors.
HNSW builds a layered graph in which each node points to its near
neighbours, and the search traverses the graph greedily from a high
layer downward. The result is sub-linear search time over millions of
vectors with very high recall, at the cost of an offline build phase
and significant memory use. The workshop uses HNSW through
[JVector](#jvector), which serves as the in-memory index over
vectors persisted by [EclipseStore](#eclipsestore).

See also: [Embedding](#embedding), [JVector](#jvector),
[Vector store](#vector-store).

### Hybrid retrieval {#hybrid-retrieval}

A retrieval strategy that combines vector search and [BM25](#bm25)
into a single ranked list. Each retriever runs independently against
the same corpus, producing two ranked lists. A fusion strategy — most
often [RRF](#rrf), sometimes a weighted-score blend — combines the
ranks into a final list. Hybrid retrieval catches both conceptual
paraphrases (where vector wins) and exact-term matches (where BM25
wins), and is more robust than either approach alone on real corpora
where queries vary in style.

See also: [BM25](#bm25), [Retriever](#retriever), [RRF](#rrf).

### Ingestion {#ingestion}

The pipeline that turns source documents into searchable
[chunks](#chunk) inside the [vector store](#vector-store). Ingestion
loads a document, hands it to a chunker, embeds each resulting chunk,
and writes the embeddings to the store along with their metadata.
This is offline work — it happens once per document, not per query —
and its quality dominates RAG quality more than retrieval tuning ever
will. Ingestion is also where keyword indexes are built, so the
[BM25](#bm25) retriever has something to query.

See also: [Chunking](#chunking), [Embedding](#embedding),
[Vector store](#vector-store).

### LLM-as-judge {#llm-as-judge}

A reranking strategy that asks a language model to score each
candidate [chunk's](#chunk) relevance to the query. The model receives
the query and one chunk at a time and returns a numerical score; the
reranker collects the scores and resorts the candidates. LLM-as-judge
is flexible — it works on any corpus without training data — and easy
to implement entirely within an [Ollama](#ollama) deployment, but it
is slow (one LLM call per candidate) and less precise than a dedicated
[cross-encoder](#cross-encoder). The workshop uses LLM-as-judge as
its only reranking implementation, because it stays within the
single-tool toolchain.

See also: [Cross-encoder](#cross-encoder), [Reranker](#reranker).

### Precision {#precision}

In retrieval, the fraction of returned [chunks](#chunk) that are
actually relevant to the query. High precision means few irrelevant
chunks slip through; low precision means the [retriever](#retriever)
returns noise. Precision and [recall](#recall) trade against each
other: a retriever tuned to return only highly confident matches has
high precision but may miss relevant chunks (low recall), while a
retriever returning many candidates has high recall but lower
precision. [Reranking](#reranker) is a way to push for both — broad
first-stage retrieval (recall) followed by tight reranking (precision).

See also: [Recall](#recall), [Reranker](#reranker), [Top-k](#top-k).

### Prompt template {#prompt-template}

A structured wrapper around the retrieved [chunks](#chunk) and user
query that instructs the language model how to answer. A good
template enforces three things at once: [grounding](#grounding) (use
only the chunks), [attribution](#attribution) (cite the chunks by
label), and [refusal](#refusal) (say "I don't know" when the chunks
fall short). The workshop ships two templates — a balanced one and a
stricter one — to make the trade-off between hallucination risk and
false-refusal rate tangible. Writing a prompt template is the single
highest-leverage change you can make in a RAG system.

See also: [Generator](#generator), [Grounding](#grounding),
[Refusal](#refusal).

### RAG {#rag}

Retrieval-Augmented Generation — an architecture that grounds a
language model's output in retrieved [chunks](#chunk) from a
searchable corpus, so the model answers from data you control rather
than from its training. A RAG system has three stages:
[ingestion](#ingestion) of source documents into a
[vector store](#vector-store), [retrieval](#retriever) of relevant
chunks at query time, and [generation](#generator) of an answer that
cites those chunks. The whole workshop is a guided construction of one
RAG system, with each module focusing on one stage or component.

See also: [Generator](#generator), [Ingestion](#ingestion),
[Retriever](#retriever).

### Recall {#recall}

In retrieval, the fraction of all relevant [chunks](#chunk) in the
corpus that the [retriever](#retriever) actually returns. High recall
means few relevant chunks are missed; low recall means the retriever
overlooks evidence that exists. Recall is the more critical of the
two basic metrics for a RAG system — a chunk that the retriever
misses cannot be cited by the [generator](#generator), no matter how
good the [prompt template](#prompt-template) is. Increasing
[top-k](#top-k) is the simplest way to raise recall, at the cost of
[precision](#precision) and [context window](#context-window) budget.

See also: [Precision](#precision), [Retriever](#retriever),
[Top-k](#top-k).

### Refusal {#refusal}

An honest "I don't know" from the language model when the retrieved
[chunks](#chunk) do not contain the answer. Refusals are better than
[hallucinations](#hallucination): a humble refusal invites a
follow-up question with better context, while a confident
fabrication costs the user trust. The workshop's
[prompt templates](#prompt-template) instruct the model to refuse, and
a refusal-detection heuristic in the [generator](#generator) flags the
answer in the UI. Refusal also bypasses the grounding check —
checking "I don't know" against the chunks would answer itself.

See also: [Grounding](#grounding), [Hallucination](#hallucination),
[Prompt template](#prompt-template).

### Reranker {#reranker}

A second-stage component that reorders the first-stage retrieval
results before they reach the [generator](#generator). The
[retriever](#retriever) returns candidates broadly, often with high
[recall](#recall) but mediocre [precision](#precision); the reranker
evaluates each candidate more carefully and pushes the most relevant
chunks to rank one. Two implementations exist in this workshop:
[LLM-as-judge](#llm-as-judge), which is what the lab uses, and the
production-grade [cross-encoder](#cross-encoder), which is mentioned
on the slides but cannot be served through [Ollama](#ollama).

See also: [Cross-encoder](#cross-encoder),
[LLM-as-judge](#llm-as-judge), [Retriever](#retriever).

### Retriever {#retriever}

The component that, given a query, returns the most relevant
[chunks](#chunk) from the [vector store](#vector-store). In this
workshop the retriever is a small interface implemented in three
ways: a vector retriever (semantic search via [embeddings](#embedding)),
a [BM25](#bm25) retriever (keyword search), and a
[hybrid retriever](#hybrid-retrieval) that fuses them. A
[reranker](#reranker) wraps a retriever to refine the order of its
results. Retrievers are the abstraction that lets the rest of the
RAG pipeline stay independent of the search strategy.

See also: [BM25](#bm25), [Hybrid retrieval](#hybrid-retrieval),
[Reranker](#reranker).

### RRF {#rrf}

Reciprocal Rank Fusion — a fusion strategy for combining two or more
ranked lists into one. Each [chunk's](#chunk) final score is the sum
of `1 / (k + rank)` across the input lists, where `k` is a small
smoothing constant (typically 60). RRF is parameter-light, robust
across corpora, and does not require score calibration between the
input retrievers. It is the default fusion strategy in
[hybrid retrieval](#hybrid-retrieval) for that reason: weighted-score
fusion can perform better with tuning, but RRF is the safer choice
out of the box.

See also: [Hybrid retrieval](#hybrid-retrieval), [Retriever](#retriever).

### Streaming {#streaming}

Serving a language model's answer token by token as it is produced,
rather than waiting for the full answer to complete. Wall-clock
latency is unchanged, but perceived latency drops dramatically — the
first token arrives in two hundred milliseconds instead of after
four seconds, and the user is reading by the time generation
finishes. The workshop streams tokens from [Ollama](#ollama) over
HTTP using newline-delimited JSON, consuming each line as it arrives
through `HttpClient.BodyHandlers.ofLines()`. No reactive framework, no
polling.

See also: [Generator](#generator), [Ollama](#ollama).

### Top-k {#top-k}

The number of [chunks](#chunk) a [retriever](#retriever) returns for
a given query. Default in this workshop is five. The trade-off is
between evidence breadth and prompt length: small top-k keeps the
prompt focused but may miss context; large top-k broadens evidence
but costs [context window](#context-window) tokens and dilutes
attention. For single-document questions, three to five chunks is
usually enough; for questions that span multiple documents, seven to
ten is a reasonable starting point. Reranking pipelines often
distinguish between first-stage-k (broad) and final-k (tight).

See also: [Context window](#context-window), [Precision](#precision),
[Recall](#recall).

### Vector store {#vector-store}

A database optimised for storing and searching high-dimensional
vectors. A vector store accepts new [embeddings](#embedding) from
[ingestion](#ingestion) and answers nearest-neighbour queries from
[retrieval](#retriever). The workshop uses
[EclipseStore](#eclipsestore) for persistence of the raw vectors and
[JVector](#jvector) for an in-memory [HNSW](#hnsw) index over them.
The two-layer design — durable vectors on disk, fast index in memory
— is typical of production vector stores; commercial alternatives
like Qdrant or Weaviate apply the same pattern at larger scale.

See also: [EclipseStore](#eclipsestore), [HNSW](#hnsw),
[JVector](#jvector).

## Tools

### EclipseStore {#eclipsestore}

A Java persistence library that stores live object graphs directly to
disk, without an ORM, schema, or relational mapping layer. Records,
lists, and maps that live in memory at runtime are the same shapes
that sleep on disk between restarts. EclipseStore loads the graph in
milliseconds at startup, with no warm-up phase. In the workshop, it
serves as the persistence layer of the [vector store](#vector-store)
— raw vectors and chunk metadata are persisted as ordinary Java
objects, while [JVector](#jvector) provides the in-memory
[HNSW](#hnsw) index on top.

See also: [JVector](#jvector), [Vector store](#vector-store).

### JVector {#jvector}

A Java library implementing [HNSW](#hnsw) vector indexes. JVector
provides the fast in-memory similarity search that
[EclipseStore](#eclipsestore) on its own does not — EclipseStore
persists the vectors as Java objects, JVector keeps them indexed for
nearest-neighbour queries. The two together make the workshop's
[vector store](#vector-store) without any external services or
network calls.

See also: [EclipseStore](#eclipsestore), [HNSW](#hnsw),
[Vector store](#vector-store).

### Ollama {#ollama}

A local runtime for language and embedding models. Ollama exposes a
small HTTP API on `localhost:11434`, with endpoints for generation,
embedding, listing local models, and pulling new models from a
registry. Everything in this workshop runs against Ollama: no cloud,
no API keys, no rate limits — just a single self-hosted process the
participant controls. Ollama supports [streaming](#streaming) over
newline-delimited JSON, which the workshop's generator consumes
directly via the JDK's `HttpClient`.

See also: [Generator](#generator), [Streaming](#streaming).

### Vaadin {#vaadin}

A Java web framework for building user interfaces entirely in Java,
with no separate JavaScript or TypeScript stack. Components are
written and composed in Java; the framework handles the
client-server round-trip, the DOM updates, and the rendering. The
workshop uses Vaadin Flow for every Lab view, with virtual threads
for streaming token updates from the [generator](#generator) into
the UI via `UI.access()`. Vaadin's component-based architecture and
server-side rendering make it a natural fit for internal tools and
admin UIs.

See also: [Generator](#generator), [Streaming](#streaming).
