package com.svenruppert.flow.views.module05;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.views.module01.DefaultLlmClient;
import com.svenruppert.flow.views.module01.LlmClient;
import com.svenruppert.flow.views.module01.LlmConfig;
import com.svenruppert.flow.views.module02.DefaultSimilarity;
import com.svenruppert.flow.views.module02.InMemoryVectorStore;
import com.svenruppert.flow.views.module03.Document;
import com.svenruppert.flow.views.module03.FileDocumentLoader;
import com.svenruppert.flow.views.module03.SentenceChunker;
import com.svenruppert.flow.views.module04.BM25Retriever;
import com.svenruppert.flow.views.module04.FusionStrategy;
import com.svenruppert.flow.views.module04.HybridRetriever;
import com.svenruppert.flow.views.module04.IngestionPipeline;
import com.svenruppert.flow.views.module04.LuceneBM25KeywordIndex;
import com.svenruppert.flow.views.module04.RetrievalHit;
import com.svenruppert.flow.views.module04.Retriever;
import com.svenruppert.flow.views.module04.VectorRetriever;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MultiFileMemoryBuffer;
import com.vaadin.flow.router.Route;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Ask Lab -- module 5's user-facing half.
 *
 * <p>Ingest a few documents, pick a retriever, ask a question, and
 * watch tokens land in the UI as Ollama streams them. Citations
 * ({@code [Chunk N]}) are picked out of the reply by
 * {@link AttributionParser} and rendered with a soft tint both inside
 * the answer text and on the sources list on the right.
 *
 * <p>Push is enabled globally on {@code AppShell} -- the streaming
 * pipeline posts tokens to the UI through {@link UI#access} from a
 * virtual thread, and the browser receives them without polling.
 */
@Route(value = Module05View.PATH, layout = MainLayout.class)
public class Module05View
    extends VerticalLayout
    implements HasLogger {

  public static final String PATH = "Module05";

  private static final String EMBEDDING_MODEL = "nomic-embed-text";
  private static final String DEFAULT_GENERATION_MODEL = "llama3.2";

  enum RetrieverChoice {
    VECTOR("Vector"), BM25("BM25"), HYBRID("Hybrid (RRF)");
    final String label;

    RetrieverChoice(String label) {
      this.label = label;
    }
  }

  // ------- dependencies -----------------------------------------------

  private final LlmClient llmClient;
  private final LlmConfig llmConfig;

  // ------- session-scoped infrastructure ------------------------------

  private InMemoryVectorStore vectorStore;
  private LuceneBM25KeywordIndex keywordIndex;
  private OllamaStreamingApi streamingApi;
  private IngestionPipeline pipeline;
  private Path uploadTempDir;
  private int documentsIngested = 0;

  // ------- ingestion widgets ------------------------------------------

  private final MultiFileMemoryBuffer uploadBuffer = new MultiFileMemoryBuffer();
  private final Upload upload = new Upload(uploadBuffer);
  private final Button ingestButton = new Button("Ingest");
  private final RadioButtonGroup<RetrieverChoice> retrieverGroup = new RadioButtonGroup<>();
  private final IntegerField retrievalKField = sizeField("Retrieval k", 5);
  private final Paragraph corpusFooter = new Paragraph("0 documents, 0 chunks");

  // ------- ask widgets ------------------------------------------------

  private final TextField queryField = new TextField("Query");
  private final ComboBox<String> modelSelector = new ComboBox<>("Model");
  private final Button askButton = new Button("Ask");
  private final Div answerDiv = new Div();
  private final Span latencyLabel = new Span("0.00 s");
  private final Span refusalBadge = new Span("refusal detected");
  private final Span groundingBadge = new Span();

  // ------- sources panel ---------------------------------------------

  private final VerticalLayout sourcesPanel = new VerticalLayout();

  // ------- options ---------------------------------------------------

  private final Checkbox strictRefusalBox = new Checkbox("Use strict refusal template");
  private final Checkbox groundingBox = new Checkbox("Run grounding check");

  public Module05View() {
    this(new DefaultLlmClient(LlmConfig.defaults()), LlmConfig.defaults());
  }

  public Module05View(LlmClient llmClient, LlmConfig llmConfig) {
    this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
    this.llmConfig = Objects.requireNonNull(llmConfig, "llmConfig");

    setSizeFull();
    setPadding(true);
    setSpacing(true);

    add(buildStyleBlock());
    add(buildHeader());
    add(buildIngestionRow());
    add(buildAskRow());
    add(buildMainRow());
    add(buildOptionsRow());

    retrieverGroup.setItems(RetrieverChoice.values());
    retrieverGroup.setItemLabelGenerator(c -> c.label);
    retrieverGroup.setValue(RetrieverChoice.HYBRID);

    ingestButton.addClickListener(e -> onIngest());
    askButton.addClickListener(e -> onAsk());

    refusalBadge.setVisible(false);
    groundingBadge.setVisible(false);
  }

  // ---------- lifecycle -----------------------------------------------

  @Override
  protected void onAttach(AttachEvent event) {
    super.onAttach(event);
    try {
      this.vectorStore = new InMemoryVectorStore(new DefaultSimilarity());
      this.keywordIndex = new LuceneBM25KeywordIndex();
      this.streamingApi = new OllamaStreamingApi(llmConfig);
      this.pipeline = new IngestionPipeline(llmClient, EMBEDDING_MODEL,
          new SentenceChunker(400), vectorStore, keywordIndex);
      this.uploadTempDir = Files.createTempDirectory("module05-upload-");
    } catch (IOException e) {
      logger().error("Could not initialise ask lab", e);
      Notification.show("Could not initialise: " + e.getMessage());
    }
    populateModelList();
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

  // ---------- layout --------------------------------------------------

  private Component buildStyleBlock() {
    String css = """
            <style>
              .chunk-ref { background: #fff3b0; padding: 0 0.2em;
                           border-radius: 3px; }
              .answer-box { font-family: ui-monospace, SFMono-Regular, monospace;
                            font-size: 0.95rem; line-height: 1.55;
                            white-space: pre-wrap; padding: 0.8em;
                            background: #fafafa; border: 1px solid #ddd;
                            border-radius: 4px; min-height: 12em; }
              .source-item { border-left: 4px solid #ddd; padding: 0.4em 0.6em;
                             margin-bottom: 0.4em; background: #fff;
                             border-top: 1px solid #eee;
                             border-right: 1px solid #eee;
                             border-bottom: 1px solid #eee; }
              .source-item.cited { border-left-color: #2e7d32; background: #f1f8f1; }
              .source-label { font-family: ui-monospace, SFMono-Regular, monospace;
                              font-size: 0.75rem; color: #2e7d32;
                              font-weight: 600; }
              .source-preview { font-size: 0.85rem; color: #333; margin-top: 0.2em; }
              .source-path { font-size: 0.75rem; color: #666; margin-top: 0.2em; }
              .badge { display: inline-block; padding: 0.1em 0.6em;
                       border-radius: 999px; font-size: 0.75rem;
                       font-family: ui-monospace, SFMono-Regular, monospace;
                       color: white; margin-left: 0.4em; }
              .badge-refusal { background: #c62828; }
              .badge-grounded { background: #2e7d32; }
              .badge-partial  { background: #f9a825; }
              .badge-not-grounded { background: #c62828; }
              .badge-unknown  { background: #757575; }
            </style>
            """;
    return new Html(css);
  }

  private Component buildHeader() {
    H3 title = new H3("Module 5 -- Ask Lab");
    Paragraph subtitle = new Paragraph(
        "Ingest a handful of documents, pick a retriever, ask a question. "
            + "Tokens stream into the answer panel as Ollama produces them; "
            + "citations are picked out of the reply and highlighted both "
            + "inline and on the sources list.");
    subtitle.getStyle().set("color", "#666").set("margin-top", "-0.4em");
    VerticalLayout box = new VerticalLayout(title, subtitle);
    box.setPadding(false);
    box.setSpacing(false);
    return box;
  }

  private Component buildIngestionRow() {
    upload.setAcceptedFileTypes("text/plain", "text/markdown", ".txt", ".md", ".markdown");
    upload.setMaxFiles(10);
    corpusFooter.getStyle()
        .set("color", "#555")
        .set("font-family", "ui-monospace, SFMono-Regular, monospace")
        .set("font-size", "0.85rem");

    HorizontalLayout row = new HorizontalLayout(
        upload, ingestButton, retrieverGroup, retrievalKField, corpusFooter);
    row.setAlignItems(FlexComponent.Alignment.CENTER);
    row.setSpacing(true);
    row.setWidthFull();
    row.setFlexGrow(1, upload);
    return row;
  }

  private Component buildAskRow() {
    queryField.setWidth("32em");
    modelSelector.setWidth("16em");

    latencyLabel.getStyle()
        .set("font-family", "ui-monospace, SFMono-Regular, monospace")
        .set("font-size", "0.85rem")
        .set("color", "#555")
        .set("margin-left", "0.6em");

    refusalBadge.addClassNames("badge", "badge-refusal");
    // Grounding badge classes are set dynamically per verdict.

    HorizontalLayout row = new HorizontalLayout(
        queryField, modelSelector, askButton,
        latencyLabel, refusalBadge, groundingBadge);
    row.setAlignItems(FlexComponent.Alignment.END);
    row.setSpacing(true);
    row.setWidthFull();
    return row;
  }

  private Component buildMainRow() {
    answerDiv.addClassName("answer-box");
    answerDiv.setWidthFull();

    VerticalLayout left = new VerticalLayout(new Span("Answer"), answerDiv);
    left.setPadding(false);
    left.setSpacing(false);
    left.getStyle().set("flex", "1").set("min-width", "0");

    sourcesPanel.setPadding(false);
    sourcesPanel.setSpacing(false);
    sourcesPanel.getStyle().set("overflow-y", "auto");

    VerticalLayout right = new VerticalLayout(new Span("Sources"), sourcesPanel);
    right.setPadding(false);
    right.setSpacing(false);
    right.getStyle().set("flex", "1").set("min-width", "0")
        .set("max-width", "28em");

    HorizontalLayout row = new HorizontalLayout(left, right);
    row.setWidthFull();
    row.setAlignItems(FlexComponent.Alignment.STRETCH);
    row.setSpacing(true);
    return row;
  }

  private Component buildOptionsRow() {
    HorizontalLayout row = new HorizontalLayout(strictRefusalBox, groundingBox);
    row.setSpacing(true);
    return row;
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
        Document document = loader.load(written);
        pipeline.ingest(document);
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

  // ---------- ask / streaming ----------------------------------------

  private void onAsk() {
    if (pipeline == null) {
      Notification.show("Pipeline not initialised yet.");
      return;
    }
    String query = queryField.getValue();
    if (query == null || query.isBlank()) {
      Notification.show("Enter a query first.");
      return;
    }
    if (pipeline.chunkRegistry().isEmpty()) {
      Notification.show("Ingest at least one document first.");
      return;
    }
    int retrievalK = valueOr(retrievalKField, 5);
    String model = Optional.ofNullable(modelSelector.getValue()).orElse(DEFAULT_GENERATION_MODEL);

    // Reset UI state for a fresh run.
    answerDiv.removeAll();
    answerDiv.setText("");
    sourcesPanel.removeAll();
    refusalBadge.setVisible(false);
    groundingBadge.setVisible(false);
    latencyLabel.setText("streaming...");
    askButton.setEnabled(false);

    Retriever retriever = buildRetriever();
    PromptTemplate template = strictRefusalBox.getValue()
        ? new StrictRefusalPromptTemplate()
        : new SimpleGroundedPromptTemplate();
    Generator generator = new DefaultGenerator(streamingApi, template);
    Optional<GroundingChecker> checker = groundingBox.getValue()
        ? Optional.of(new DefaultGroundingChecker(llmClient))
        : Optional.empty();
    RagPipeline rag = new RagPipeline(retriever, generator, checker);

    UI ui = UI.getCurrent();
    StringBuilder live = new StringBuilder();

    Thread.ofVirtual().name("m5-ask-" + System.currentTimeMillis()).start(() -> {
      try {
        GeneratedAnswer answer = rag.ask(query, retrievalK, model, token -> {
          live.append(token);
          String snapshot = live.toString();
          ui.access(() -> answerDiv.setText(snapshot));
        });
        ui.access(() -> finaliseAnswer(answer));
      } catch (RuntimeException e) {
        logger().warn("ask-worker failed: {}", e.getMessage());
        ui.access(() -> {
          Notification.show("Ask failed: " + e.getMessage());
          askButton.setEnabled(true);
          latencyLabel.setText("error");
        });
      }
    });
  }

  private Retriever buildRetriever() {
    VectorRetriever vector = new VectorRetriever(
        llmClient, EMBEDDING_MODEL, vectorStore, pipeline.chunkRegistry());
    BM25Retriever bm25 = new BM25Retriever(keywordIndex, pipeline.chunkRegistry());
    return switch (retrieverGroup.getValue()) {
      case VECTOR -> vector;
      case BM25 -> bm25;
      case HYBRID -> new HybridRetriever(vector, bm25,
          new FusionStrategy.ReciprocalRankFusion(60.0),
          Math.max(valueOr(retrievalKField, 5) * 2, 10));
    };
  }

  private void finaliseAnswer(GeneratedAnswer answer) {
    // Render the final answer with [Chunk N] citations highlighted.
    Set<Integer> cited = new HashSet<>(answer.citedChunkIndices());
    String html = AttributionParser.highlight(escapeHtml(answer.text()), cited);
    answerDiv.removeAll();
    answerDiv.setText("");
    answerDiv.add(new Html("<div>" + html + "</div>"));

    latencyLabel.setText(String.format(Locale.ROOT, "%.2f s",
        answer.latencyMillis() / 1000.0));

    refusalBadge.setVisible(answer.refusalDetected());
    renderGroundingBadge(answer.groundingCheck());
    renderSources(answer.usedHits(), cited);
    askButton.setEnabled(true);
  }

  private void renderGroundingBadge(Optional<GroundingResult> maybeCheck) {
    if (maybeCheck.isEmpty()) {
      groundingBadge.setVisible(false);
      return;
    }
    GroundingResult check = maybeCheck.get();
    String cls = switch (check.verdict()) {
      case GROUNDED -> "badge-grounded";
      case PARTIAL -> "badge-partial";
      case NOT_GROUNDED -> "badge-not-grounded";
      case UNKNOWN -> "badge-unknown";
    };
    // Reset and set classes.
    groundingBadge.setClassName("");
    groundingBadge.addClassNames("badge", cls);
    groundingBadge.setText(check.verdict().name().toLowerCase(Locale.ROOT).replace('_', ' '));
    groundingBadge.getElement().setAttribute("title",
        check.rationale().isEmpty() ? "no rationale returned" : check.rationale());
    groundingBadge.setVisible(true);
  }

  private void renderSources(List<RetrievalHit> hits, Set<Integer> citedIndices) {
    sourcesPanel.removeAll();
    int number = 1;
    for (RetrievalHit hit : hits) {
      int zeroBased = number - 1;
      boolean cited = citedIndices.contains(zeroBased);

      Div item = new Div();
      item.addClassName("source-item");
      if (cited) item.addClassName("cited");

      Span label = new Span("[Chunk " + number + "]   " + chunkIdFor(hit));
      label.addClassName("source-label");

      Div preview = new Div();
      preview.addClassName("source-preview");
      preview.setText(preview(hit.chunk().text(), 120));

      item.add(label, preview);
      Object headingPath = hit.chunk().metadata().get(
          com.svenruppert.flow.views.module03.Chunk.HEADING_PATH);
      if (headingPath != null && !headingPath.toString().isEmpty()) {
        Div path = new Div();
        path.addClassName("source-path");
        path.setText(headingPath.toString());
        item.add(path);
      }
      sourcesPanel.add(item);
      number++;
    }
  }

  // ---------- helpers -----------------------------------------------

  private void populateModelList() {
    List<String> names = llmClient.listModels().orElse(List.of(DEFAULT_GENERATION_MODEL));
    if (names.isEmpty()) names = List.of(DEFAULT_GENERATION_MODEL);
    modelSelector.setItems(names);
    String preferred = names.stream()
        .filter(n -> n.contains("llama") || n.contains("qwen"))
        .findFirst()
        .orElse(names.get(0));
    modelSelector.setValue(preferred);
  }

  private static IntegerField sizeField(String label, int defaultValue) {
    IntegerField field = new IntegerField(label);
    field.setValue(defaultValue);
    field.setMin(1);
    field.setWidth("7em");
    return field;
  }

  private static int valueOr(IntegerField field, int fallback) {
    Integer v = field.getValue();
    return (v == null || v <= 0) ? fallback : v;
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

  /**
   * Escapes HTML metacharacters without touching the {@code [Chunk N]}
   * markers themselves -- those are left as plain text so
   * {@link AttributionParser#highlight} can wrap them afterwards.
   */
  private static String escapeHtml(String s) {
    StringBuilder out = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '&' -> out.append("&amp;");
        case '<' -> out.append("&lt;");
        case '>' -> out.append("&gt;");
        default -> out.append(c);
      }
    }
    return out.toString();
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
}
