package com.svenruppert.flow.views.module05;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.WorkshopDefaults;
import com.svenruppert.flow.views.help.ExpandableHelp;
import com.svenruppert.flow.views.help.HelpEntry;
import com.svenruppert.flow.views.help.MarkdownSupport;
import com.svenruppert.flow.views.help.ParameterDocs;
import com.svenruppert.flow.views.help.RetrievalSourcesPanel;
import com.svenruppert.flow.views.help.ThinkingPanel;
import com.svenruppert.flow.views.help.ThrottledUiBuffer;
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
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.UploadHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
@CssImport("./styles/module05-view.css")
// Shares the hljs-loader module with the Module 1 chat view so code
// blocks inside the assistant answer get the same syntax-highlighting
// treatment. The loader is idempotent (guards on a window flag).
@JsModule("./highlight/hljs-loader.js")
public class Module05View
    extends VerticalLayout
    implements HasLogger {

  public static final String PATH = "Module05";

  private static final String EMBEDDING_MODEL =
      WorkshopDefaults.DEFAULT_EMBEDDING_MODEL;
  private static final String DEFAULT_GENERATION_MODEL =
      WorkshopDefaults.DEFAULT_GENERATION_MODEL;

  enum RetrieverChoice {
    VECTOR("m05.retriever.vector"), BM25("m05.retriever.bm25"), HYBRID("m05.retriever.hybrid");
    final String labelKey;

    RetrieverChoice(String labelKey) {
      this.labelKey = labelKey;
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

  /**
   * File names that have been uploaded but not yet ingested. We track
   * the "still pending" subset ourselves and drive the chip strip from
   * it -- same pattern as Module 4's Retrieval Lab.
   */
  private final Set<String> pendingFilenames = new LinkedHashSet<>();
  private final HorizontalLayout pendingChips = new HorizontalLayout();

  /**
   * Bytes of every still-uncommitted upload, keyed by filename.
   * Populated by the {@link UploadHandler#inMemory} callback and
   * drained when the user clicks "Ingest". Replaces Vaadin 25's
   * deprecated {@code MultiFileMemoryBuffer}.
   */
  private final Map<String, byte[]> pendingBytes = new LinkedHashMap<>();
  private final Upload upload = new Upload(
      UploadHandler.inMemory((metadata, bytes) -> {
        pendingBytes.put(metadata.fileName(), bytes);
        pendingFilenames.add(metadata.fileName());
        renderPendingChips();
      }));
  private final Button ingestButton = new Button();
  private final RadioButtonGroup<RetrieverChoice> retrieverGroup = new RadioButtonGroup<>();
  private final IntegerField retrievalKField = sizeField(5);
  private final Paragraph corpusFooter = new Paragraph();

  // ------- ask widgets ------------------------------------------------

  private final TextField queryField = new TextField();
  private final ComboBox<String> modelSelector = new ComboBox<>();
  private final Button askButton = new Button();
  private final Div answerDiv = new Div();
  private final Span latencyLabel = new Span();
  private final Span refusalBadge = new Span();
  private final Span groundingBadge = new Span();

  // ------- progress (stage indicator) ---------------------------------

  /**
   * End-to-end progress bar driven by {@link RagPipeline.StageListener}.
   * The pipeline has at most three phases (retrieve, generate, ground),
   * so we map each transition to a fractional value -- the bar and the
   * status label advance together as the pipeline hands off between
   * stages.
   */
  private final ProgressBar askProgress = new ProgressBar();
  private final Span askStatus = new Span();
  private final HorizontalLayout progressRow = new HorizontalLayout();

  // ------- thinking panel (side channel) ------------------------------

  private final ThinkingPanel thinkingPanel = new ThinkingPanel();

  // ------- sources panel ---------------------------------------------

  private final RetrievalSourcesPanel sourcesPanel = new RetrievalSourcesPanel();

  // ------- options ---------------------------------------------------

  private final Checkbox strictRefusalBox = new Checkbox();
  private final Checkbox groundingBox = new Checkbox();

  public Module05View() {
    this(new DefaultLlmClient(LlmConfig.defaults()), LlmConfig.defaults());
  }

  public Module05View(LlmClient llmClient, LlmConfig llmConfig) {
    this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
    this.llmConfig = Objects.requireNonNull(llmConfig, "llmConfig");

    // Set labels and texts
    ingestButton.setText(getTranslation("m05.button.ingest"));
    retrievalKField.setLabel(getTranslation("m05.field.retrieval.k"));
    queryField.setLabel(getTranslation("m05.field.query"));
    modelSelector.setLabel(getTranslation("m05.field.model"));
    askButton.setText(getTranslation("m05.button.ask"));
    latencyLabel.setText(getTranslation("m05.latency.initial"));
    refusalBadge.setText(getTranslation("m05.badge.refusal"));
    strictRefusalBox.setLabel(getTranslation("m05.checkbox.strict.refusal"));
    groundingBox.setLabel(getTranslation("m05.checkbox.grounding"));
    corpusFooter.addClassName("m05-corpus-footer");

    setSizeFull();
    setPadding(true);
    setSpacing(true);

    add(buildHeader());
    add(buildIngestionRow());
    add(buildAskRow());
    add(buildProgressRow());
    add(buildMainRow());
    add(buildOptionsRow());

    retrieverGroup.setItems(RetrieverChoice.values());
    retrieverGroup.setItemLabelGenerator(c -> getTranslation(c.labelKey));
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
      Notification.show(getTranslation("m05.error.init", e.getMessage()));
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

  private Component buildHeader() {
    H3 title = new H3(getTranslation("m05.header.title"));
    Paragraph subtitle = new Paragraph(getTranslation("m05.header.subtitle"));
    subtitle.addClassName("m05-header-subtitle");
    VerticalLayout box = new VerticalLayout(title, subtitle);
    box.setPadding(false);
    box.setSpacing(false);
    return box;
  }

  /**
   * Corpus zone, two rows:
   * <ol>
   *   <li>Upload (compact) + pending-file chip strip + Ingest button +
   *       corpus footer.</li>
   *   <li>Retriever radio + retrieval-k field, placed beneath the upload
   *       so the eye doesn't have to race from left (upload) to right
   *       (retriever setting).</li>
   * </ol>
   */
  private Component buildIngestionRow() {
    upload.setAcceptedFileTypes("text/plain", "text/markdown", ".txt", ".md", ".markdown");
    upload.setMaxFiles(10);
    upload.addClassName("compact-upload");
    upload.setDropLabel(new Span(getTranslation("m05.upload.drop")));
    // Pending-chip update is wired through the UploadHandler in the
    // field initialiser; no succeeded-listener needed here.

    pendingChips.addClassName("pending-chips");
    pendingChips.setSpacing(false);
    renderPendingChips();

    HorizontalLayout uploadRow = new HorizontalLayout(
        upload, pendingChips, ingestButton, corpusFooter);
    uploadRow.setAlignItems(FlexComponent.Alignment.CENTER);
    uploadRow.setSpacing(true);
    uploadRow.setWidthFull();
    uploadRow.setFlexGrow(1, pendingChips);

    HorizontalLayout retrieverRow = new HorizontalLayout(
        withHelp(retrieverGroup, ParameterDocs.M5_RETRIEVER_MODE),
        withHelp(retrievalKField, ParameterDocs.M5_RETRIEVAL_K));
    retrieverRow.setAlignItems(FlexComponent.Alignment.START);
    retrieverRow.setSpacing(true);

    VerticalLayout box = new VerticalLayout(uploadRow, retrieverRow);
    box.setPadding(false);
    box.setSpacing(false);
    box.setWidthFull();
    return box;
  }

  /**
   * Rebuilds the chip strip from {@link #pendingFilenames}. Each chip
   * removes its file from the queue when clicked.
   */
  private void renderPendingChips() {
    pendingChips.removeAll();
    if (pendingFilenames.isEmpty()) {
      Span empty = new Span(getTranslation("m05.chip.empty"));
      empty.addClassName("pending-chip-empty");
      pendingChips.add(empty);
      return;
    }
    for (String name : pendingFilenames) {
      Span chip = new Span(name + "  \u00D7");
      chip.addClassName("pending-chip");
      chip.getElement().setAttribute("title", getTranslation("m05.chip.remove.title"));
      chip.addClickListener(e -> {
        pendingFilenames.remove(name);
        renderPendingChips();
      });
      pendingChips.add(chip);
    }
  }

  private Component buildAskRow() {
    queryField.addClassName("m05-query-field");
    modelSelector.addClassName("m05-model-selector");
    latencyLabel.addClassName("m05-latency-label");
    refusalBadge.addClassNames("badge", "badge-refusal");
    // Grounding badge classes are set dynamically per verdict.

    HorizontalLayout row = new HorizontalLayout(
        queryField,
        withHelp(modelSelector, ParameterDocs.M5_GENERATION_MODEL),
        askButton,
        latencyLabel, refusalBadge, groundingBadge);
    row.setAlignItems(FlexComponent.Alignment.START);
    row.setSpacing(true);
    row.setWidthFull();
    return row;
  }

  /**
   * Pairs a user-facing control with its inline help panel in a
   * tight vertical column.
   */
  private static VerticalLayout withHelp(Component control, HelpEntry entry) {
    VerticalLayout column = new VerticalLayout(control, ExpandableHelp.of(entry));
    column.setPadding(false);
    column.setSpacing(false);
    column.setWidth(null);
    return column;
  }

  private Component buildProgressRow() {
    askProgress.addClassName("m05-ask-progress");
    askProgress.setMin(0.0);
    askProgress.setMax(1.0);
    askProgress.setValue(0.0);
    askStatus.addClassName("m05-ask-status");
    progressRow.setAlignItems(FlexComponent.Alignment.CENTER);
    progressRow.setSpacing(true);
    progressRow.add(askProgress, askStatus);
    progressRow.setVisible(false);
    return progressRow;
  }

  private Component buildMainRow() {
    answerDiv.addClassName("answer-box");
    answerDiv.setWidthFull();

    VerticalLayout left = new VerticalLayout(
        thinkingPanel.component(),
        new Span(getTranslation("m05.label.answer")),
        answerDiv);
    left.setPadding(false);
    left.setSpacing(false);
    left.addClassName("m05-answer-column");

    VerticalLayout right = new VerticalLayout(
        new Span(getTranslation("m05.label.sources")),
        sourcesPanel);
    right.setPadding(false);
    right.setSpacing(false);
    right.addClassName("m05-sources-column");

    HorizontalLayout row = new HorizontalLayout(left, right);
    row.setWidthFull();
    row.setAlignItems(FlexComponent.Alignment.STRETCH);
    row.setSpacing(true);
    return row;
  }

  private Component buildOptionsRow() {
    HorizontalLayout row = new HorizontalLayout(
        withHelp(strictRefusalBox, ParameterDocs.M5_PROMPT_TEMPLATE),
        withHelp(groundingBox, ParameterDocs.M5_GROUNDING_CHECK));
    row.setAlignItems(FlexComponent.Alignment.START);
    row.setSpacing(true);
    return row;
  }

  // ---------- ingestion ----------------------------------------------

  private void onIngest() {
    if (pipeline == null) {
      Notification.show(getTranslation("m05.error.not.init"));
      return;
    }
    if (pendingFilenames.isEmpty()) {
      Notification.show(getTranslation("m05.error.no.files"));
      return;
    }
    FileDocumentLoader loader = new FileDocumentLoader();
    int before = pipeline.chunkRegistry().size();
    int processed = 0;
    // Snapshot so we can mutate pendingFilenames in the finally clause.
    for (String fileName : List.copyOf(pendingFilenames)) {
      byte[] bytes = pendingBytes.get(fileName);
      if (bytes == null) {
        pendingFilenames.remove(fileName);
        continue;
      }
      try {
        Path written = uploadTempDir.resolve(fileName);
        Files.write(written, bytes);
        Document document = loader.load(written);
        pipeline.ingest(document);
        documentsIngested++;
        processed++;
      } catch (IOException e) {
        logger().warn("Ingest of {} failed: {}", fileName, e.getMessage());
        Notification.show(getTranslation("m05.error.ingest", fileName, e.getMessage()));
      } finally {
        pendingFilenames.remove(fileName);
        pendingBytes.remove(fileName);
      }
    }
    renderPendingChips();
    int added = pipeline.chunkRegistry().size() - before;
    Notification.show(getTranslation("m05.notify.ingested", processed, added));
    refreshCorpusFooter();
  }

  private void refreshCorpusFooter() {
    int chunks = pipeline != null ? pipeline.chunkRegistry().size() : 0;
    corpusFooter.setText(getTranslation("m05.corpus.footer", documentsIngested, chunks));
  }

  // ---------- ask / streaming ----------------------------------------

  private void onAsk() {
    if (pipeline == null) {
      Notification.show(getTranslation("m05.error.not.init"));
      return;
    }
    String query = queryField.getValue();
    if (query == null || query.isBlank()) {
      Notification.show(getTranslation("m05.error.query.empty"));
      return;
    }
    if (pipeline.chunkRegistry().isEmpty()) {
      Notification.show(getTranslation("m05.error.no.docs"));
      return;
    }
    int retrievalK = valueOr(retrievalKField, 5);
    String model = Optional.ofNullable(modelSelector.getValue()).orElse(DEFAULT_GENERATION_MODEL);

    // Reset UI state for a fresh run. Add the `.streaming` class so
    // the answer renders in monospace with pre-wrap while tokens land
    // token-by-token; `finaliseAnswer` drops it and renders Markdown.
    answerDiv.removeAll();
    answerDiv.setText("");
    answerDiv.addClassName("streaming");
    thinkingPanel.resetForStreaming();
    sourcesPanel.removeAll();
    refusalBadge.setVisible(false);
    groundingBadge.setVisible(false);
    latencyLabel.setText(getTranslation("m05.status.streaming"));
    askButton.setEnabled(false);

    // Stage indicator: start at 0% with a "Starting" label.
    boolean groundingEnabled = groundingBox.getValue();
    askProgress.setValue(0.0);
    askStatus.setText(getTranslation("m05.status.starting"));
    progressRow.setVisible(true);

    Retriever retriever = buildRetriever();
    PromptTemplate template = strictRefusalBox.getValue()
        ? new StrictRefusalPromptTemplate()
        : new SimpleGroundedPromptTemplate();
    Generator generator = new DefaultGenerator(streamingApi, template);
    Optional<GroundingChecker> checker = groundingEnabled
        ? Optional.of(new DefaultGroundingChecker(llmClient))
        : Optional.empty();
    RagPipeline rag = new RagPipeline(retriever, generator, checker);

    UI ui = UI.getCurrent();
    ThrottledUiBuffer liveAnswer =
        new ThrottledUiBuffer(ui, 75, answerDiv::setText);
    ThrottledUiBuffer liveThinking =
        new ThrottledUiBuffer(ui, 75, thinkingPanel::showStreaming);

    Thread.ofVirtual().name("m5-ask-" + System.currentTimeMillis()).start(() -> {
      try {
        GeneratedAnswer answer = rag.ask(query, retrievalK, model,
            token -> {
              liveAnswer.append(token);
            },
            thinkingToken -> {
              liveThinking.append(thinkingToken);
            },
            stage -> ui.access(() -> applyStage(stage, groundingEnabled)));
        ui.access(() -> finaliseAnswer(answer));
      } catch (RuntimeException e) {
        logger().warn("ask-worker failed: {}", e.getMessage());
        ui.access(() -> {
          Notification.show(getTranslation("m05.error.ask", e.getMessage()));
          askButton.setEnabled(true);
          latencyLabel.setText(getTranslation("m05.status.error"));
          askStatus.setText(getTranslation("m05.status.error.detail", e.getMessage()));
        });
      }
    });
  }

  /**
   * Maps a pipeline phase transition to a progress-bar value and
   * status-label text. The mapping is coarse on purpose: the phases
   * are few, their durations wildly different, and the intent is to
   * tell participants what the pipeline is <em>doing</em> right now,
   * not to estimate a true time-to-completion.
   */
  private void applyStage(RagPipeline.Stage stage, boolean groundingEnabled) {
    double fraction = switch (stage) {
      case RETRIEVAL_STARTED -> 0.05;
      case RETRIEVAL_FINISHED -> 0.15;
      case GENERATION_STARTED -> 0.20;
      case GENERATION_FINISHED -> groundingEnabled ? 0.75 : 1.0;
      case GROUNDING_STARTED -> 0.80;
      case GROUNDING_FINISHED -> 1.0;
      case DONE -> 1.0;
    };
    String label = switch (stage) {
      case RETRIEVAL_STARTED -> getTranslation("m05.stage.retrieval.started");
      case RETRIEVAL_FINISHED -> getTranslation("m05.stage.retrieval.finished");
      case GENERATION_STARTED -> getTranslation("m05.stage.generation.started");
      case GENERATION_FINISHED -> groundingEnabled
          ? getTranslation("m05.stage.generation.finished.grounding")
          : getTranslation("m05.stage.done");
      case GROUNDING_STARTED -> getTranslation("m05.stage.grounding.started");
      case GROUNDING_FINISHED -> getTranslation("m05.stage.grounding.finished");
      case DONE -> getTranslation("m05.stage.done");
    };
    askProgress.setValue(fraction);
    askStatus.setText(label);
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
    Set<Integer> cited = new HashSet<>(answer.citedChunkIndices());
    String html = MarkdownSupport.renderSafeHtml(answer.text());
    String withCitations = AttributionParser.highlight(html, cited);

    answerDiv.removeAll();
    answerDiv.setText("");
    answerDiv.removeClassName("streaming");
    answerDiv.add(MarkdownSupport.htmlDiv(withCitations));
    MarkdownSupport.highlightCodeBlocks(answerDiv);

    latencyLabel.setText(getTranslation("m05.latency.format",
        String.format(Locale.ROOT, "%.2f", answer.latencyMillis() / 1000.0)));

    refusalBadge.setVisible(answer.refusalDetected());
    renderGroundingBadge(answer.groundingCheck());
    sourcesPanel.renderWithCitations(answer.usedHits(), cited, this::chunkIdFor);
    finaliseThinking(answer.thinking());
    askButton.setEnabled(true);
  }

  /**
   * Snapshots the thinking content into the side panel after the stream
   * ends. When the model did not emit any thinking, the panel stays
   * hidden -- exactly the pre-thinking look.
   */
  private void finaliseThinking(String thinking) {
    if (thinking == null || thinking.isEmpty()) {
      thinkingPanel.finalise(thinking, "");
      return;
    }
    String html = MarkdownSupport.renderSafeHtml(thinking);
    thinkingPanel.finalise(thinking, html);
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
    String verdictText = switch (check.verdict()) {
      case GROUNDED -> getTranslation("m05.verdict.grounded");
      case PARTIAL -> getTranslation("m05.verdict.partial");
      case NOT_GROUNDED -> getTranslation("m05.verdict.not.grounded");
      case UNKNOWN -> getTranslation("m05.verdict.unknown");
    };
    groundingBadge.setClassName("");
    groundingBadge.addClassNames("badge", cls);
    groundingBadge.setText(verdictText);
    groundingBadge.getElement().setAttribute("title",
        check.rationale().isEmpty()
            ? getTranslation("m05.grounding.no.rationale")
            : check.rationale());
    groundingBadge.setVisible(true);
  }

  // ---------- helpers -----------------------------------------------

  private void populateModelList() {
    List<String> names = llmClient.listModels().orElse(List.of(DEFAULT_GENERATION_MODEL));
    if (names.isEmpty()) names = List.of(DEFAULT_GENERATION_MODEL);
    modelSelector.setItems(names);
    modelSelector.setValue(WorkshopDefaults.preferredGenerationModel(names));
  }

  private static IntegerField sizeField(int defaultValue) {
    IntegerField field = new IntegerField();
    field.setValue(defaultValue);
    field.setMin(1);
    field.addClassName("m05-size-field");
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
