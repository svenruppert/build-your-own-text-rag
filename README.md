# Build Your Own Text RAG

A hands-on Vaadin Flow workshop that walks through building a text-based
**Retrieval-Augmented Generation (RAG)** system step by step -- from a
bare LLM client all the way to a production-style product view.

---

## Workshop structure

The project is organised into six modules that correspond to successive
layers of the RAG pipeline.  Each module adds one piece and extends the
previous:

| Module | View | Topic |
|--------|------|-------|
| 01 | `Module01` | LLM client -- HTTP calls to Ollama, streaming responses |
| 02 | `Module02` | Vector store -- cosine similarity, in-memory and EclipseStore-backed |
| 03 | `Module03` | Document loading and chunking (fixed-size, sentence, structure-aware) |
| 04 | `Module04` | Retrieval Lab -- Vector, BM25, Hybrid (RRF), LLM-as-judge reranker |
| 05 | `Module05` | Ask Lab -- end-to-end RAG with streaming answers and grounding check |
| 06 | `Module06` | RAG Product -- product framing, persistent store, no tuning knobs |

---

## Prerequisites

| Tool | Version / Note |
|------|----------------|
| Java | 25 (Temurin recommended) |
| Maven | 3.9.9 or later |
| Vaadin | 25.1.1 (managed by POM) |
| [Ollama](https://ollama.ai/) | Running locally on `http://localhost:11434` |

### Required Ollama models

```
ollama pull nomic-embed-text-v2-moe   # text embeddings (all modules)
ollama pull gemma4:e4b                # generation, reranking + grounding
```

Any Ollama-compatible embedding and chat model can be swapped in via
`LlmConfig` -- the workshop defaults are shown above.

---

## Starting the application

```bash
# Run in development mode (hot reload, dev tools)
mvn jetty:run
```

Then open `http://localhost:8080` in your browser.

---

## Running the tests

```bash
# Unit tests (no Ollama required)
mvn test

# Include live Ollama smoke tests (requires a running Ollama instance)
mvn test -Dworkshop.live=true
```

The live smoke test (`LiveOllamaSmokeTest`) is skipped by default so the
build does not depend on a local Ollama installation in CI.

---

## Demo data

Sample documents for the workshop are located in `_demo-data/`.  Drop any
`.txt` or `.md` file into the upload area of Module 03 - 06 to try the
pipeline with your own content.

---

## Project tooling

- **Mutation testing:** `mvn org.pitest:pitest-maven:mutationCoverage`
- **SBOM generation:** `mvn cyclonedx:makeAggregateBom`
- **Dependency updates:** `mvn versions:display-dependency-updates`

---

## License

Apache License 2.0 -- see [LICENSE](LICENSE).
