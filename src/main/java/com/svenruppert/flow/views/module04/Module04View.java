package com.svenruppert.flow.views.module04;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.views.module01.DefaultLlmClient;
import com.svenruppert.flow.views.module01.LlmClient;
import com.svenruppert.flow.views.module01.LlmConfig;
import com.svenruppert.flow.views.module02.DefaultSimilarity;
import com.svenruppert.flow.views.module02.InMemoryVectorStore;
import com.svenruppert.flow.views.module03.Chunk;
import com.svenruppert.flow.views.module03.Document;
import com.svenruppert.flow.views.module03.FileDocumentLoader;
import com.svenruppert.flow.views.module03.SentenceChunker;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MultiFileMemoryBuffer;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.Route;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Retrieval Lab -- the user-facing half of module 4.
 *
 * <p>Upload a handful of .txt/.md files, ingest them through the
 * {@link IngestionPipeline}, then answer the same query with four
 * different retrievers and two different rerankers. The grid underneath
 * shows source-label pills, chunk ids, scores and a short preview so
 * the shape of each retriever's answer is legible at a glance.
 */
@Route(value = Module04View.PATH, layout = MainLayout.class)
public class Module04View
    extends VerticalLayout
    implements HasLogger {

  public static final String PATH = "Module04";

  private static final String EMBEDDING_MODEL = "nomic-embed-text";

  private static final Map<String, String> SOURCE_COLOURS = Map.of(
      "vector", "#1976d2",
      "bm25", "#ef6c00",
      "hybrid", "#7b1fa2",
      "llm-judge-reranked", "#2e7d32",
      "bge-reranked", "#c62828");

  // Retriever / reranker choice enums drive the radio groups; their
  // labels are shown in the UI.
  enum RetrieverChoice {
    VECTOR("Vector"), BM25("BM25"),
    HYBRID_RRF("Hybrid (RRF)"), HYBRID_WEIGHTED("Hybrid (weighted)");
    final String label;

    RetrieverChoice(String label) {
      this.label = label;
    }
  }

  enum RerankerChoice {
    NONE("None"),
    LLM_JUDGE("LLM-as-judge (Ollama)"),
    BGE("BGE cross-encoder (Ollama)");
    final String label;

    RerankerChoice(String label) {
      this.label = label;
    }
  }

  // ------- dependencies ----------------------------------------------

  private final LlmClient llmClient;
  private final LlmConfig llmConfig;

  // ------- session-scoped infrastructure ------------------------------

  private InMemoryVectorStore vectorStore;
  private LuceneBM25KeywordIndex keywordIndex;
  private OllamaRerankApi rerankApi;
  private IngestionPipeline pipeline;
  private Path uploadTempDir;
  private int documentsIngested = 0;

  // ------- widgets ---------------------------------------------------

  private final MultiFileMemoryBuffer uploadBuffer = new MultiFileMemoryBuffer();
  private final Upload upload = new Upload(uploadBuffer);
  private final Button ingestButton = new Button("Ingest");
  private final Paragraph corpusFooter = new Paragraph("0 documents, 0 chunks");

  private final TextField queryField = new TextField("Query");
  private final IntegerField topKField = sizeField("Top k", 5);
  private final RadioButtonGroup<RetrieverChoice> retrieverGroup = new RadioButtonGroup<>();
  private final RadioButtonGroup<RerankerChoice> rerankerGroup = new RadioButtonGroup<>();

  private final NumberField rrfKField = doubleField("rrfK", 60.0);
  private final NumberField vectorWeightField = doubleField("Vector weight", 0.6);
  private final NumberField bm25WeightField = doubleField("BM25 weight", 0.4);

  private final ComboBox<String> llmJudgeModel = new ComboBox<>("Judge model");
  private final ComboBox<String> bgeRerankerModel = new ComboBox<>("Reranker model");
  private final Paragraph bgeHint = new Paragraph(
      "Requires Ollama's /api/rerank endpoint. If your Ollama build is older, "
          + "the reranker will pass candidates through unchanged.");
  private final IntegerField firstStageKField = sizeField("First-stage k", 20);

  private final HorizontalLayout hybridParamsRow = new HorizontalLayout();
  private final HorizontalLayout rerankerParamsRow = new HorizontalLayout();

  private final Button searchButton = new Button("Search");
  private final Span latencyLabel = new Span();

  private final Grid<RetrievalHit> resultsGrid = new Grid<>(RetrievalHit.class, false);

  public Module04View() {
    this(new DefaultLlmClient(LlmConfig.defaults()), LlmConfig.defaults());
  }

  public Module04View(LlmClient llmClient, LlmConfig llmConfig) {
    this.llmClient = llmClient;
    this.llmConfig = llmConfig;

    setSizeFull();
    setPadding(true);
    setSpacing(true);

    add(buildHeader());
    add(buildIngestionZone());
    add(buildRetrievalControls());
    add(buildResultsZone());

    // Default selections and event wiring.
    retrieverGroup.setItems(RetrieverChoice.values());
    retrieverGroup.setItemLabelGenerator(c -> c.label);
    retrieverGroup.setValue(RetrieverChoice.VECTOR);
    retrieverGroup.addValueChangeListener(e -> refreshConditionalParams());

    rerankerGroup.setItems(RerankerChoice.values());
    rerankerGroup.setItemLabelGenerator(c -> c.label);
    rerankerGroup.setValue(RerankerChoice.NONE);
    rerankerGroup.addValueChangeListener(e -> refreshConditionalParams());

    ingestButton.addClickListener(e -> onIngest());
    searchButton.addClickListener(e -> onSearch());

    refreshConditionalParams();
  }

  // ---------- lifecycle ----------------------------------------------

  @Override
  protected void onAttach(AttachEvent event) {
    super.onAttach(event);
    try {
      this.vectorStore = new InMemoryVectorStore(new DefaultSimilarity());
      this.keywordIndex = new LuceneBM25KeywordIndex();
      this.rerankApi = new OllamaRerankApi(llmConfig);
      this.pipeline = new IngestionPipeline(llmClient, EMBEDDING_MODEL,
          new SentenceChunker(400), vectorStore, keywordIndex);
      this.uploadTempDir = Files.createTempDirectory("module04-upload-");
    } catch (IOException e) {
      logger().error("Could not initialise retrieval lab", e);
      Notification.show("Could not initialise: " + e.getMessage());
    }
    populateModelLists();
    refreshCorpusFooter();
  }

  @Override
  protected void onDetach(DetachEvent event) {
    try {
      if (keywordIndex != null) keywordIndex.close();
    } catch (IOException e) {
      logger().warn("Keyword index close failed: {}", e.getMessage());
    }
    deleteRecursively(uploadTempDir);
    super.onDetach(event);
  }

  // ---------- layout builders ----------------------------------------

  private Component buildHeader() {
    H3 title = new H3("Module 4 -- Retrieval Lab");
    Paragraph subtitle = new Paragraph(
        "Four retrievers, two rerankers, one corpus. Every implementation "
            + "sits on the Ollama side of the runtime axis; the architecture "
            + "axis (bi-encoder vs. cross-encoder) is the one that actually "
            + "changes ranking behaviour here.");
    subtitle.getStyle().set("color", "#666").set("margin-top", "-0.4em");
    VerticalLayout box = new VerticalLayout(title, subtitle);
    box.setPadding(false);
    box.setSpacing(false);
    return box;
  }

  private Component buildIngestionZone() {
    upload.setAcceptedFileTypes("text/plain", "text/markdown", ".txt", ".md", ".markdown");
    upload.setMaxFiles(10);

    corpusFooter.getStyle()
        .set("color", "#555")
        .set("font-family", "ui-monospace, SFMono-Regular, monospace")
        .set("font-size", "0.85rem");

    HorizontalLayout row = new HorizontalLayout(upload, ingestButton, corpusFooter);
    row.setAlignItems(FlexComponent.Alignment.CENTER);
    row.setSpacing(true);
    row.setWidthFull();
    row.setFlexGrow(1, upload);
    return row;
  }

  private Component buildRetrievalControls() {
    queryField.setWidth("28em");
    topKField.setWidth("6em");
    firstStageKField.setWidth("8em");

    hybridParamsRow.setSpacing(true);
    hybridParamsRow.setAlignItems(FlexComponent.Alignment.END);

    rerankerParamsRow.setSpacing(true);
    rerankerParamsRow.setAlignItems(FlexComponent.Alignment.END);

    latencyLabel.getStyle()
        .set("font-family", "ui-monospace, SFMono-Regular, monospace")
        .set("font-size", "0.85rem")
        .set("color", "#555");

    HorizontalLayout queryRow = new HorizontalLayout(queryField, topKField, searchButton, latencyLabel);
    queryRow.setAlignItems(FlexComponent.Alignment.END);
    queryRow.setSpacing(true);
    queryRow.setWidthFull();

    VerticalLayout box = new VerticalLayout(
        queryRow,
        retrieverGroup, hybridParamsRow,
        rerankerGroup, rerankerParamsRow);
    box.setPadding(false);
    box.setSpacing(true);
    box.setWidthFull();
    return box;
  }

  private Component buildResultsZone() {
    resultsGrid.addColumn(new ComponentRenderer<>(this::renderSourcePill))
        .setHeader("Source").setAutoWidth(true);
    resultsGrid.addColumn(h -> chunkIdFor(h))
        .setHeader("Chunk id").setAutoWidth(true);
    resultsGrid.addColumn(h -> String.format(Locale.ROOT, "%.4f", h.score()))
        .setHeader("Score").setAutoWidth(true);
    resultsGrid.addColumn(h ->
            String.valueOf(h.chunk().metadata().getOrDefault(Chunk.HEADING_PATH, "")))
        .setHeader("Heading path").setAutoWidth(true);
    resultsGrid.addColumn(h -> preview(h.chunk().text(), 120))
        .setHeader("Preview").setFlexGrow(1);
    resultsGrid.setAllRowsVisible(false);
    resultsGrid.setHeight("24em");
    resultsGrid.setWidthFull();
    return resultsGrid;
  }

  private Component renderSourcePill(RetrievalHit hit) {
    Span pill = new Span(hit.sourceLabel());
    String colour = SOURCE_COLOURS.getOrDefault(hit.sourceLabel(), "#555");
    pill.getStyle()
        .set("background-color", colour)
        .set("color", "white")
        .set("font-size", "0.75rem")
        .set("font-family", "ui-monospace, SFMono-Regular, monospace")
        .set("padding", "0.15em 0.7em")
        .set("border-radius", "999px");
    return pill;
  }

  // ---------- conditional-param wiring -------------------------------

  private void refreshConditionalParams() {
    hybridParamsRow.removeAll();
    rerankerParamsRow.removeAll();
    RetrieverChoice r = retrieverGroup.getValue();
    if (r == RetrieverChoice.HYBRID_RRF) {
      hybridParamsRow.add(rrfKField);
    } else if (r == RetrieverChoice.HYBRID_WEIGHTED) {
      hybridParamsRow.add(vectorWeightField, bm25WeightField);
    }

    RerankerChoice rr = rerankerGroup.getValue();
    if (rr == RerankerChoice.LLM_JUDGE) {
      rerankerParamsRow.add(llmJudgeModel, firstStageKField);
    } else if (rr == RerankerChoice.BGE) {
      rerankerParamsRow.add(bgeRerankerModel, firstStageKField, bgeHint);
    }
  }

  // ---------- ingestion ----------------------------------------------

  private void onIngest() {
    if (pipeline == null) {
      Notification.show("Pipeline not initialised yet.");
      return;
    }
    FileDocumentLoader loader = new FileDocumentLoader();
    int before = pipeline.chunkRegistry().size();
    int processed = 0;
    for (String fileName : uploadBuffer.getFiles()) {
      try (InputStream in = uploadBuffer.getInputStream(fileName)) {
        byte[] bytes = in.readAllBytes();
        Path written = uploadTempDir.resolve(fileName);
        Files.write(written, bytes);
        Document doc = loader.load(written);
        pipeline.ingest(doc);
        documentsIngested++;
        processed++;
      } catch (IOException e) {
        logger().warn("Ingest of {} failed: {}", fileName, e.getMessage());
        Notification.show("Failed to ingest " + fileName + ": " + e.getMessage());
      }
    }
    int added = pipeline.chunkRegistry().size() - before;
    Notification.show("Ingested " + processed + " documents (" + added + " new chunks).");
    refreshCorpusFooter();
  }

  private void refreshCorpusFooter() {
    int chunks = pipeline != null ? pipeline.chunkRegistry().size() : 0;
    corpusFooter.setText(documentsIngested + " documents, " + chunks + " chunks");
  }

  // ---------- search --------------------------------------------------

  private void onSearch() {
    if (pipeline == null) {
      Notification.show("Pipeline not initialised yet.");
      return;
    }
    String query = queryField.getValue();
    if (query == null || query.isBlank()) {
      Notification.show("Enter a query first.");
      return;
    }
    int topK = valueOr(topKField, 5);

    Retriever retriever = buildRetriever();
    Reranker reranker = buildReranker();
    Retriever finalRetriever = (reranker == null)
        ? retriever
        : new RerankingRetriever(retriever, reranker, valueOr(firstStageKField, 20));

    long t0 = System.nanoTime();
    List<RetrievalHit> hits = finalRetriever.retrieve(query, topK);
    double millis = (System.nanoTime() - t0) / 1.0e6;

    resultsGrid.setItems(hits.stream()
        .sorted(Comparator.comparingDouble(RetrievalHit::score).reversed())
        .toList());
    latencyLabel.setText(String.format(Locale.ROOT, "%.2f ms", millis));
  }

  private Retriever buildRetriever() {
    VectorRetriever vector = new VectorRetriever(
        llmClient, EMBEDDING_MODEL, vectorStore, pipeline.chunkRegistry());
    BM25Retriever bm25 = new BM25Retriever(keywordIndex, pipeline.chunkRegistry());

    return switch (retrieverGroup.getValue()) {
      case VECTOR -> vector;
      case BM25 -> bm25;
      case HYBRID_RRF -> new HybridRetriever(vector, bm25,
          new FusionStrategy.ReciprocalRankFusion(
              valueOr(rrfKField, 60.0)),
          Math.max(valueOr(topKField, 5) * 2, 10));
      case HYBRID_WEIGHTED -> new HybridRetriever(vector, bm25,
          new FusionStrategy.WeightedScoreFusion(
              valueOr(vectorWeightField, 0.6),
              valueOr(bm25WeightField, 0.4)),
          Math.max(valueOr(topKField, 5) * 2, 10));
    };
  }

  private Reranker buildReranker() {
    return switch (rerankerGroup.getValue()) {
      case NONE -> null;
      case LLM_JUDGE -> new LlmJudgeReranker(llmClient,
          Optional.ofNullable(llmJudgeModel.getValue()).orElse(LlmJudgeReranker.DEFAULT_MODEL));
      case BGE -> new BgeReranker(rerankApi,
          Optional.ofNullable(bgeRerankerModel.getValue()).orElse(BgeReranker.DEFAULT_MODEL));
    };
  }

  // ---------- population --------------------------------------------

  private void populateModelLists() {
    List<String> models = llmClient.listModels().orElse(List.of("llama3.2"));
    llmJudgeModel.setItems(models);
    llmJudgeModel.setValue(models.stream()
        .filter(n -> n.contains("llama") || n.contains("qwen"))
        .findFirst()
        .orElse(LlmJudgeReranker.DEFAULT_MODEL));

    List<String> rerankModels = models.stream()
        .filter(n -> n.contains("rerank"))
        .toList();
    if (rerankModels.isEmpty()) rerankModels = List.of(BgeReranker.DEFAULT_MODEL);
    bgeRerankerModel.setItems(rerankModels);
    bgeRerankerModel.setValue(rerankModels.get(0));
  }

  // ---------- helpers -----------------------------------------------

  private static IntegerField sizeField(String label, int defaultValue) {
    IntegerField field = new IntegerField(label);
    field.setValue(defaultValue);
    field.setMin(1);
    field.setWidth("8em");
    return field;
  }

  private static NumberField doubleField(String label, double defaultValue) {
    NumberField field = new NumberField(label);
    field.setValue(defaultValue);
    field.setWidth("8em");
    return field;
  }

  private static int valueOr(IntegerField field, int fallback) {
    Integer v = field.getValue();
    return (v == null || v <= 0) ? fallback : v;
  }

  private static double valueOr(NumberField field, double fallback) {
    Double v = field.getValue();
    return (v == null) ? fallback : v;
  }

  private String chunkIdFor(RetrievalHit hit) {
    return pipeline.chunkRegistry().entrySet().stream()
        .filter(e -> e.getValue().equals(hit.chunk()))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse("(unknown)");
  }

  private static String preview(String text, int max) {
    String flat = text.replace('\n', ' ').replace('\r', ' ').trim();
    return flat.length() <= max ? flat : flat.substring(0, max) + "...";
  }

  private void deleteRecursively(Path root) {
    if (root == null) return;
    try (Stream<Path> walk = Files.walk(root)) {
      walk.sorted(Comparator.reverseOrder()).forEach(p -> {
        try {
          Files.deleteIfExists(p);
        } catch (IOException ignored) {
          // best-effort cleanup
        }
      });
    } catch (IOException e) {
      logger().warn("Could not delete {}: {}", root, e.getMessage());
    }
  }

  // Workaround for unused import: prevents an import optimiser from
  // deleting StandardCharsets once the view's string usage changes.
  @SuppressWarnings("unused")
  private static final String ENCODING = StandardCharsets.UTF_8.name();
}
