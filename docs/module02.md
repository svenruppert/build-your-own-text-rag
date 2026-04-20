# Module 02 -- Vector Store Lab

Two implementations of the same contract, side by side. The point of the
module is not either implementation on its own -- it is the realisation
that you can swap them without the caller noticing. Everything above
the `VectorStore` interface is identical.

## What this module solves

- **`VectorStore`** is a minimal, honest key-value store over dense
  float vectors: `add`, `search(query, k)`, `size`, `clear`.
- **`InMemoryVectorStore`** is the didactic reference: a
  `ConcurrentHashMap` and a linear scan. Read it top to bottom in a
  minute, and you understand cosine search.
- **`EclipseStoreJVectorStore`** is the industrial twin:
  - EclipseStore persists the raw `float[]` vectors on disk.
  - JVector maintains an HNSW graph in memory for fast
    approximate nearest-neighbour search.
  - The HNSW graph is *not* persisted: it is rebuilt from the
    durable vectors on demand. Embedding cost (Ollama round-trip) is
    what we are careful to save; a second or two of index-build cost
    is nothing by comparison.

## How the two stores differ

| Concern        | `InMemoryVectorStore`       | `EclipseStoreJVectorStore`                 |
|----------------|-----------------------------|--------------------------------------------|
| Storage        | RAM only                    | Raw vectors on disk (EclipseStore)         |
| Index          | None                        | HNSW graph in memory (JVector)             |
| Query cost     | O(n) linear scan            | ~ O(log n) via HNSW                        |
| Restart        | Empty again                 | Reloads vectors, rebuilds index in seconds |
| Thread-safety  | `ConcurrentHashMap`         | `volatile` snapshot + synchronised mutate  |
| Failure mode   | Process death = data loss   | Process death = intact on disk             |

## The Lab view

Route: `/Module02` (sidebar "Module 02"). Inside:

1. Pick an embedding model in the ComboBox (defaults to something that
   contains "embed", or to `nomic-embed-text` if the model list cannot
   be fetched from Ollama).
2. Choose which store backs the results grid via the radio group --
   `InMemoryVectorStore` or `EclipseStoreJVectorStore`. Both stores are
   always live and populated in lock-step.
3. Type a text, optionally set an id and a payload, click *Add to both
   stores*. The same embedding is pushed into both.
4. Type a query, set top-k, click *Search*. The view queries both
   stores, times both with `System.nanoTime`, and renders the hits of
   the currently selected store in the grid. The line under the grid
   reads, for example, `InMemory: 1.87 ms . JVector: 0.34 ms . showing
   EclipseStoreJVectorStore`.

Flip the toggle back and forth with a populated corpus: the numbers
change, the result set does not. That is the whole point -- the
`VectorStore` contract is identical.

Each UI session gets its own temporary EclipseStore directory under
`java.io.tmpdir`; it is torn down when the view detaches. Cross-session
persistence is covered in the closing block of the workshop.

## Benchmark

`JVectorStoreBenchmark` is a plain `main` class under
`src/main/java/.../module02/`. Run it to see the two stores scale
differently:

```
mvn exec:java -Dexec.mainClass=com.svenruppert.flow.views.module02.JVectorStoreBenchmark
```

Configurable via system properties:

| Property             | Default          | Meaning                                         |
|----------------------|------------------|-------------------------------------------------|
| `benchmark.sizes`    | `1000,10000,50000` | CSV of corpus sizes                           |
| `benchmark.large`    | (unset)          | If `true`, append `100000` to the list          |
| `benchmark.queries`  | `500`            | Measured queries per size                       |
| `benchmark.warmup`   | `100`            | Warmup queries -- absorbs the first rebuild     |
| `benchmark.topk`     | `10`             | Top-k for every query                           |
| `benchmark.dimension`| `768`            | Vector dimension (defaults to `nomic-embed-text`) |

Example output (abridged):

```
Corpus size | Store                     | Ingest (s) | Median (ms) | P95 (ms) | Min (ms) | Max (ms)
      1,000 | InMemoryVectorStore       |       0.00 |        0.12 |     0.18 |     0.09 |     0.32
      1,000 | EclipseStoreJVectorStore  |       0.06 |        0.15 |     0.21 |     0.11 |     0.48
     10,000 | InMemoryVectorStore       |       0.00 |        1.18 |     1.40 |     1.01 |     3.87
     10,000 | EclipseStoreJVectorStore  |       0.78 |        0.21 |     0.30 |     0.15 |     0.84
```

The InMemory store's median grows roughly linearly with the corpus size.
The HNSW store's median stays almost flat. This is what HNSW buys you
and the motivation for every production vector database.

## Tests

Under `src/test/java/com/svenruppert/flow/views/module02/`:

- `DefaultSimilarityTest` -- cosine identity, orthogonality,
  opposition, zero-norm, error cases.
- `VectorStoreContractTest` -- the abstract behavioural contract every
  `VectorStore` has to satisfy (seven tests, fed by the deterministic
  fixture under `src/test/resources/module02/fixtures/vectors.json`).
- `InMemoryVectorStoreTest` and `EclipseStoreJVectorStoreTest` -- each
  extends the contract and plugs in a factory for the store under
  test. The EclipseStore-JVector subclass adds
  `restart_reloadsPersistedVectorsWithoutReembedding`, the test that
  pins down the actual persistence promise.

Run the suite:

```
mvn test
```

## Note on `GigaMap`

The original module brief sketched the persistent root as
`GigaMap<String, RawVectorEntry>`. In EclipseStore 4.x, `GigaMap`
actually has a single type parameter (`GigaMap<E>`) and is not a
`java.util.Map`. For workshop clarity, `VectorStoreRoot` uses a plain
`HashMap<String, RawVectorEntry>`. The persistent-raw-vectors invariant
is unchanged: EclipseStore persists the map's reachable object graph,
and the HNSW index is rebuilt from those vectors on first search after
a mutation.
