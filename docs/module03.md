# Module 03 -- Ingestion & Chunking

The ingestion phase quietly decides a surprising amount of retrieval
quality. This module stops glossing over it and makes four concrete
chunkers touchable -- literally, through the lab view.

## What this module solves

- Turning a file on disk into a **plain-text `Document`** with
  structured metadata (content type, Markdown heading hierarchy).
- Four **chunking strategies** against the same uniform
  [`Chunker`](../src/main/java/com/svenruppert/flow/views/module03/Chunker.java)
  contract:
    1. `FixedSizeChunker` -- the naive baseline, equal-length blocks.
    2. `OverlappingChunker` -- the workhorse; adjacent chunks share a
       configurable tail so a sentence straddling a boundary survives
       in at least one chunk.
    3. `SentenceChunker` -- sentence-aware, packs sentences until the
       next one would overshoot a target size. Never splits mid-sentence;
       oversized sentences become their own (oversized) chunk.
    4. `StructureAwareChunker` -- snaps chunk boundaries to Markdown
       heading transitions and further splits long sections sentence
       by sentence. Every chunk carries its heading breadcrumb path
       in `metadata()`.
- A **Chunking Lab** view where participants load or paste text, pick
  a chunker, and see the colour-coded result with overlap zones visible
  at a glance.

## Why the `commonmark-java` dependency

`commonmark-java` is the only new dependency in this module (in fact it
was pulled in back in module 1 for chat rendering, so no POM change was
needed). Writing yet another mini-Markdown parser would cost us time we
rather spend on the RAG lessons, and it would miss edge cases we care
about -- fenced code blocks, link targets, nested emphasis. commonmark
gives us a proper AST plus an `AbstractVisitor` we extend cleanly.

## The Chunking Lab

Route: `/Module03` (sidebar entry "Module 03"). Inside:

1. Paste text or upload a `.txt` / `.md` file into the text area on the
   left. A sample Markdown document is pre-loaded so the view is useful
   from the first click.
2. Pick a chunker via the tabs. The *Structure-aware* tab auto-disables
   for inputs without Markdown headings.
3. Set parameters (chunk size, overlap, target size) and press
   **Chunk it**.
4. The info line reports chunk count, total coverage, mean chunk size
   and how many characters fall inside overlap zones.
5. The visualisation renders the source text as a `<span>` chain with
   inline styles: single-chunk regions wear a soft tint of that chunk's
   palette colour; overlap regions get a stronger tint plus a
   bottom-border in the second chunk's colour, so overlap is visible as
   "denser and two-toned".
6. The legend under the visualisation lists every chunk. Clicking a
   legend entry promotes that chunk's regions to a heavier tint so the
   eye can pick out exactly which positions belong to which chunk.

All rendering is server-side Java. The `<style>` block is kept inline
and commented so trainers can tweak palette colours on the fly.

### Canonical text

When the input is Markdown, the view runs
`MarkdownTextExtractor.extract` first and chunks the **extracted plain
text** (that is also what an embedding model would see). Pure plain
text is passed through verbatim -- no surprising whitespace
normalisation.

## Design decision: metadata on `Chunk`

The spec asked us to choose between extending the `Chunk` record with
optional metadata vs. introducing a separate `StructuredChunk` wrapper.
We extended `Chunk`:

```java
public record Chunk(int index, String text,
                    int startOffset, int endOffset,
                    Map<String, Object> metadata) { ... }
```

- Every chunker returns the same type; downstream code never has to
  type-check.
- Basic chunkers use the convenience constructor
  `new Chunk(index, text, start, end)`, metadata defaults to the
  immutable empty map.
- `StructureAwareChunker` attaches a single entry under
  `Chunk.HEADING_PATH` (constant on `Chunk`). Additional structure-aware
  chunkers in future modules can write further keys without changing
  the class hierarchy.

A separate wrapper type would have forced every caller to check "is
this chunk structured or basic?". Extending the record keeps the
`Chunker.chunk` signature uniform -- which was the whole point of the
contract in the first place.

## Tests

Under
[`src/test/java/com/svenruppert/flow/views/module03/`](../src/test/java/com/svenruppert/flow/views/module03/):

- `FileDocumentLoaderTest` -- extension dispatch, UTF-8 round-trip,
  fallback to plain text.
- `MarkdownTextExtractorTest` -- headings, code blocks, lists, link
  URL dropping, emphasis stripping.
- `ChunkerSanityTests` -- shared invariants
  (`assertChunksCoverEntireInput`, `assertOffsetsConsistent`,
  `assertIndicesSequential`) used by every chunker test.
- `FixedSizeChunkerTest`, `OverlappingChunkerTest`,
  `SentenceChunkerTest`, `StructureAwareChunkerTest` -- the four
  implementations against their own behavioural specs.

Run the suite:

```
mvn test
```

## Fixtures

`src/test/resources/documents/`:
- `simple.txt` -- short English plain-text fixture with UTF-8 accents.
- `simple.md` -- exercises every Markdown extractor path: three heading
  levels, a fenced code block, a bullet list, a link, inline emphasis
  and strong emphasis.
