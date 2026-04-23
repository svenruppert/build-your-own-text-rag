package com.svenruppert.flow.views.module05;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.WorkshopDefaults;
import com.svenruppert.flow.util.AsyncTask;
import com.svenruppert.flow.util.UploadTempDir;
import com.svenruppert.flow.views.help.ExpandableHelp;
import com.svenruppert.flow.views.help.MarkdownSupport;
import com.svenruppert.flow.views.help.ParameterDocs;
import com.svenruppert.flow.views.help.PendingUploadZone;
import com.svenruppert.flow.views.help.RetrievalSourcesPanel;
import com.svenruppert.flow.views.help.StageProgress;
import com.svenruppert.flow.views.help.ThinkingPanel;
import com.svenruppert.flow.views.help.ThrottledUiBuffer;
import com.svenruppert.flow.views.module01.DefaultLlmClient;
import com.svenruppert.flow.views.module01.LlmClient;
import com.svenruppert.flow.views.module01.LlmConfig;
import com.svenruppert.flow.views.module03.Document;
import com.svenruppert.flow.views.module03.FileDocumentLoader;
import com.svenruppert.flow.views.module04.FusionStrategy;
import com.svenruppert.flow.views.module04.IngestionPipeline;
import com.svenruppert.flow.views.module04.RetrievalLab;
import com.svenruppert.flow.views.module04.Retriever;
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
import com.vaadin.flow.router.Route;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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

  /**
   * Bundles the in-memory vector store, Lucene keyword index and
   * {@link IngestionPipeline} wired to a common chunk registry, plus
   * the retriever factories. Owns the Lucene index lifecycle.
   */
  private RetrievalLab lab;
  /** Convenience alias for {@code lab.pipeline()} -- heavily referenced. */
  private IngestionPipeline pipeline;
  private OllamaStreamingApi streamingApi;
  private UploadTempDir uploadTempDir;
  private int documentsIngested = 0;

  // ------- ingestion widgets ------------------------------------------

  /**
   * Composite widget for the compact Upload, pending-file chip strip
   * and Ingest button. Constructed in the view ctor where translations
   * are available.
   */
  private PendingUploadZone uploadZone;
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
    this(DefaultLlmClient.withDefaults(), LlmConfig.defaults());
  }

  public Module05View(LlmClient llmClient, LlmConfig llmConfig) {
    this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
    this.llmConfig = Objects.requireNonNull(llmConfig, "llmConfig");

    // Set labels and texts
    this.uploadZone = new PendingUploadZone(new PendingUploadZone.Labels(
        getTranslation("m05.upload.drop"),
        getTranslation("m05.chip.empty"),
        getTranslation("m05.chip.remove.title"),
        getTranslation("m05.button.ingest")));
    uploadZone.ingestButton().addClickListener(e -> onIngest());
    retrievalKField.setLabel(getTranslation("m05.field.retrieval.k"));
    queryField.setLabel(getTranslation("m05.field.query"));
    modelSelector.setLabel(getTranslation("m05.field.model"));
    askButton.setText(getTranslation("m05.button.ask"));
    latencyLabel.setText(getTranslation("m05.latency.initial"));
    refusalBadge.setText(getTranslation("m05.badge.refusal"));
    strictRefusalBox.setLabel(getTranslation("m05.checkbox.strict.refusal"));
    groundingBox.setLabel(getTranslation("m05.checkbox.grounding"));
    corpusFooter.addClassName("m05-corpus-footer");

    // Width only -- AppLayout scrolls the page natively; setSizeFull()
    // would pin the view to viewport height and clip overflow.
    setWidthFull();
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

    askButton.addClickListener(e -> onAsk());

    refusalBadge.setVisible(false);
    groundingBadge.setVisible(false);
  }

  // ---------- lifecycle -----------------------------------------------

  @Override
  protected void onAttach(AttachEvent event) {
    super.onAttach(event);
    try {
      this.lab = RetrievalLab.create(llmClient, EMBEDDING_MODEL);
      this.pipeline = lab.pipeline();
      this.streamingApi = new OllamaStreamingApi(llmConfig);
      this.uploadTempDir = UploadTempDir.create("module05-upload-");
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
      if (lab != null) lab.close();
    } catch (IOException e) {
      logger().warn("Keyword index close failed: {}", e.getMessage());
    }
    if (uploadTempDir != null) uploadTempDir.close(msg -> logger().warn(msg));
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
    HorizontalLayout uploadRow = new HorizontalLayout(uploadZone, corpusFooter);
    uploadRow.setAlignItems(FlexComponent.Alignment.CENTER);
    uploadRow.setSpacing(true);
    uploadRow.setWidthFull();
    uploadRow.setFlexGrow(1, uploadZone);

    HorizontalLayout retrieverRow = new HorizontalLayout(
        ExpandableHelp.pair(retrieverGroup, ParameterDocs.M5_RETRIEVER_MODE),
        ExpandableHelp.pair(retrievalKField, ParameterDocs.M5_RETRIEVAL_K));
    retrieverRow.setAlignItems(FlexComponent.Alignment.START);
    retrieverRow.setSpacing(true);

    VerticalLayout box = new VerticalLayout(uploadRow, retrieverRow);
    box.setPadding(false);
    box.setSpacing(false);
    box.setWidthFull();
    return box;
  }

  private Component buildAskRow() {
    queryField.addClassName("m05-query-field");
    modelSelector.addClassName("m05-model-selector");
    latencyLabel.addClassName("m05-latency-label");
    refusalBadge.addClassNames("badge", "badge-refusal");
    // Grounding badge classes are set dynamically per verdict.

    HorizontalLayout row = new HorizontalLayout(
        queryField,
        ExpandableHelp.pair(modelSelector, ParameterDocs.M5_GENERATION_MODEL),
        askButton,
        latencyLabel, refusalBadge, groundingBadge);
    row.setAlignItems(FlexComponent.Alignment.START);
    row.setSpacing(true);
    row.setWidthFull();
    return row;
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
        ExpandableHelp.pair(strictRefusalBox, ParameterDocs.M5_PROMPT_TEMPLATE),
        ExpandableHelp.pair(groundingBox, ParameterDocs.M5_GROUNDING_CHECK));
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
    if (!uploadZone.hasPending()) {
      Notification.show(getTranslation("m05.error.no.files"));
      return;
    }
    FileDocumentLoader loader = new FileDocumentLoader();
    int before = pipeline.chunkRegistry().size();
    int[] processed = {0};
    uploadZone.drain(
        (fileName, bytes) -> {
          Path written = uploadTempDir.resolve(fileName);
          Files.write(written, bytes);
          Document document = loader.load(written);
          pipeline.ingest(document);
          documentsIngested++;
          processed[0]++;
        },
        (fileName, msg) -> {
          logger().warn("Ingest of {} failed: {}", fileName, msg);
          Notification.show(getTranslation("m05.error.ingest", fileName, msg));
        });
    int added = pipeline.chunkRegistry().size() - before;
    Notification.show(getTranslation("m05.notify.ingested", processed[0], added));
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

    AsyncTask.runInBackground(ui, "m5-ask",
        () -> {
          GeneratedAnswer answer = rag.ask(query, retrievalK, model,
              liveAnswer::append,
              liveThinking::append,
              stage -> ui.access(() -> applyStage(stage, groundingEnabled)));
          ui.access(() -> finaliseAnswer(answer));
        },
        e -> {
          logger().warn("ask-worker failed: {}", e.getMessage());
          Notification.show(getTranslation("m05.error.ask", e.getMessage()));
          askButton.setEnabled(true);
          latencyLabel.setText(getTranslation("m05.status.error"));
          askStatus.setText(getTranslation("m05.status.error.detail", e.getMessage()));
        });
  }

  /**
   * Maps a pipeline phase transition to a progress-bar value and
   * status-label text. Fractions and label suffixes come from the
   * shared {@link StageProgress} table; the suffix is bound to the
   * {@code m05.stage.*} key space locally.
   */
  private void applyStage(RagPipeline.Stage stage, boolean groundingEnabled) {
    StageProgress.Phase phase = StageProgress.phase(stage, groundingEnabled);
    askProgress.setValue(phase.fraction());
    askStatus.setText(getTranslation("m05.stage." + phase.labelSuffix()));
  }

  private Retriever buildRetriever() {
    return switch (retrieverGroup.getValue()) {
      case VECTOR -> lab.vectorRetriever();
      case BM25 -> lab.bm25Retriever();
      case HYBRID -> lab.hybridRetriever(
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
    sourcesPanel.renderWithCitations(answer.usedHits(), cited,
        hit -> pipeline.idOf(hit.chunk()));
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

}
