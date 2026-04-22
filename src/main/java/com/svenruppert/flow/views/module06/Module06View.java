package com.svenruppert.flow.views.module06;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.views.help.ExpandableHelp;
import com.svenruppert.flow.views.help.MarkdownSupport;
import com.svenruppert.flow.views.help.ParameterDocs;
import com.svenruppert.flow.views.help.RetrievalSourcesPanel;
import com.svenruppert.flow.views.help.ThinkingPanel;
import com.svenruppert.flow.views.help.ThrottledUiBuffer;
import com.svenruppert.flow.views.module01.DefaultLlmClient;
import com.svenruppert.flow.views.module01.LlmClient;
import com.svenruppert.flow.views.module01.LlmConfig;
import com.svenruppert.flow.views.module02.EclipseStoreJVectorStore;
import com.svenruppert.flow.views.module03.SentenceChunker;
import com.svenruppert.flow.views.module05.GeneratedAnswer;
import com.svenruppert.flow.views.module05.OllamaStreamingApi;
import com.svenruppert.flow.views.module05.RagPipeline;
import com.svenruppert.flow.views.module05.SimpleGroundedPromptTemplate;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.server.streams.UploadMetadata;
import com.vaadin.flow.component.dependency.CssImport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Module 6 -- product framing of the RAG stack built in modules 1-5.
 *
 * <p>The lab-style views of modules 3-5 put every knob on the page:
 * which chunker, which retriever, which prompt template, whether to
 * run the grounding check. A product does not ask its users any of
 * that; it takes a document, takes a question, and answers. This view
 * exposes precisely those two verbs:
 * <ul>
 *   <li>drop a file into the upload panel -- it is ingested
 *       immediately, no "Ingest" button,</li>
 *   <li>type a question into the ask box and hit Enter -- tokens
 *       stream into the answer panel as Ollama produces them.</li>
 * </ul>
 *
 * <p>Two affordances keep a laptop-speed run legible:
 * <ul>
 *   <li>a three-step indicator (retrieve / generate / ground) that
 *       lights up as the pipeline moves from phase to phase, so a user
 *       knows the system is working rather than stuck,</li>
 *   <li>a collapsible "Thinking" panel that surfaces reasoning tokens
 *       from thinking-capable models on a side channel, without
 *       cluttering the answer.</li>
 * </ul>
 *
 * <p>Every wiring decision lives in {@link ProductConfig} and is
 * applied through {@link RagSystemBuilder}. This class is a thin UI
 * over {@link RagSystem}'s two public methods.
 */
@CssImport("./styles/module06-view.css")
@Route(value = Module06View.PATH, layout = MainLayout.class)
public class Module06View
    extends VerticalLayout
    implements HasLogger {

  public static final String PATH = "Module06";

  /**
   * On-disk location of the module-6 vector store. Per-module
   * directory so each workshop chapter is isolated.
   */
  private static final Path STORAGE_DIR = Path.of("eclipsestore-data", "module06");

  private final LlmClient llmClient;
  private final LlmConfig llmConfig;

  // ------- product-side infrastructure --------------------------------

  private EclipseStoreJVectorStore vectorStore;
  private OllamaStreamingApi streamingApi;
  private RagSystem ragSystem;

  // ------- UI widgets ------------------------------------------------

  /**
   * Vaadin 25 dropped {@code MultiFileMemoryBuffer} + succeeded-listener
   * in favour of {@link UploadHandler}. The in-memory handler hands us
   * the bytes directly, so we ingest straight from the callback -- no
   * buffer to drain, no listener to wire.
   */
  private final Upload upload = new Upload(
      UploadHandler.inMemory(this::ingestUploaded));
  private final Span statusLabel = new Span();
  private final Paragraph corpusFooter = new Paragraph();

  /**
   * One pill per ingested source. Clicking the pill's close affordance
   * drops that source from the store. Strip is rebuilt from
   * {@link RagSystem#listSources()} after every ingest or remove, so a
   * stale UI cache can't diverge from the real registry.
   */
  private final HorizontalLayout documentChips = new HorizontalLayout();
  private final Button clearAllButton = new Button();

  private final TextField queryField = new TextField();
  private final Button askButton = new Button();
  private final Div answerDiv = new Div();
  private final RetrievalSourcesPanel sourcesPanel = new RetrievalSourcesPanel();

  // ------- step indicator + progress bar -----------------------------

  /**
   * Three pill-shaped chips, one per pipeline phase. States:
   * <ul>
   *   <li>{@code idle} -- phase hasn't started yet (grey),</li>
   *   <li>{@code active} -- phase is running right now (blue, pulsing),</li>
   *   <li>{@code done} -- phase is finished (green check).</li>
   * </ul>
   * The grounding chip is hidden while grounding is off, so the visual
   * matches the actual pipeline.
   */
  private final Span stepRetrieve = new Span();
  private final Span stepGenerate = new Span();
  private final Span stepGround = new Span();
  private final ProgressBar progress = new ProgressBar();
  private final Span progressStatus = new Span();
  private final HorizontalLayout stepsRow = new HorizontalLayout(
      stepRetrieve, stepGenerate, stepGround);
  private final HorizontalLayout progressRow = new HorizontalLayout(
      progress, progressStatus);

  // ------- thinking panel (side channel) -----------------------------

  private final ThinkingPanel thinkingPanel = new ThinkingPanel();

  public Module06View() {
    this(new DefaultLlmClient(LlmConfig.defaults()), LlmConfig.defaults());
  }

  public Module06View(LlmClient llmClient, LlmConfig llmConfig) {
    this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
    this.llmConfig = Objects.requireNonNull(llmConfig, "llmConfig");

    statusLabel.setText(getTranslation("m06.status.no.docs"));
    corpusFooter.setText(getTranslation("m06.corpus.footer", 0, 0));
    clearAllButton.setText(getTranslation("m06.button.clear"));
    askButton.setText(getTranslation("m06.button.ask"));
    stepRetrieve.setText(getTranslation("m06.step.retrieve"));
    stepGenerate.setText(getTranslation("m06.step.generate"));
    stepGround.setText(getTranslation("m06.step.ground"));

    setSizeFull();
    setPadding(true);
    setSpacing(true);

    add(buildHeader());
    add(buildCorpusRow());
    add(buildDocumentChipRow());
    add(buildAskRow());
    add(buildStepsRow());
    add(buildProgressRow());
    add(buildMainRow());

    askButton.addClickListener(e -> onAsk());
    clearAllButton.addClickListener(e -> onClearAll());
  }

  // ---------- lifecycle ----------------------------------------------

  @Override
  protected void onAttach(AttachEvent event) {
    super.onAttach(event);
    try {
      this.vectorStore = new EclipseStoreJVectorStore(STORAGE_DIR);
      this.streamingApi = new OllamaStreamingApi(llmConfig);
      this.ragSystem = RagSystem.builder()
          .llmClient(llmClient)
          .vectorStore(vectorStore)
          .chunker(new SentenceChunker(ProductConfig.CHUNK_TARGET_SIZE))
          .promptTemplate(new SimpleGroundedPromptTemplate())
          .streamingApi(streamingApi)
          .build();
    } catch (RuntimeException e) {
      logger().error("Could not initialise RagSystem", e);
      Notification.show(getTranslation("m06.error.init", e.getMessage()));
    }
    refreshCorpusFooter();
    renderDocumentChips();
  }

  @Override
  protected void onDetach(DetachEvent event) {
    try {
      if (ragSystem != null) ragSystem.close();
    } catch (IOException e) {
      logger().warn("RagSystem close failed: {}", e.getMessage());
    }
    if (vectorStore != null) vectorStore.close();
    super.onDetach(event);
  }

  // ---------- layout -------------------------------------------------

  private Component buildHeader() {
    H3 title = new H3(getTranslation("m06.header.title"));
    Paragraph subtitle = new Paragraph(getTranslation("m06.header.subtitle"));
    subtitle.addClassName("m06-header-subtitle");

    // Product defaults are opaque to the user by design, but which
    // embedder and which generation model are in use matters for
    // troubleshooting ("why is this slow?", "why doesn't it know X?").
    // Surface them as monospace chips in the header.
    Span embedderChip = new Span(getTranslation("m06.chip.embedder", ProductConfig.DEFAULT_EMBEDDING_MODEL));
    Span generatorChip = new Span(getTranslation("m06.chip.generator", ProductConfig.DEFAULT_GENERATION_MODEL));
    embedderChip.addClassName("model-chip");
    generatorChip.addClassName("model-chip");
    HorizontalLayout modelRow = new HorizontalLayout(embedderChip, generatorChip);
    modelRow.setSpacing(true);
    modelRow.setPadding(false);
    modelRow.addClassName("m06-model-row");

    VerticalLayout box = new VerticalLayout(title, subtitle, modelRow);
    box.setPadding(false);
    box.setSpacing(false);
    return box;
  }

  private Component buildCorpusRow() {
    upload.setAcceptedFileTypes("text/plain", "text/markdown", ".txt", ".md", ".markdown");
    upload.setMaxFiles(20);
    upload.setDropLabel(new Span(getTranslation("m06.upload.drop")));
    upload.addClassName("m06-upload");
    // Ingestion is wired through the UploadHandler in the field
    // initialiser -- no listener needed here.

    statusLabel.addClassName("status-label");
    corpusFooter.addClassName("m06-corpus-footer");

    // Upload is the product's only user-facing control, so its help
    // sits directly beneath it rather than mixed in with the status
    // and corpus-size labels.
    VerticalLayout uploadColumn = new VerticalLayout(
        upload, ExpandableHelp.of(ParameterDocs.M6_UPLOAD));
    uploadColumn.setPadding(false);
    uploadColumn.setSpacing(false);
    uploadColumn.setWidth(null);

    HorizontalLayout row = new HorizontalLayout(uploadColumn, statusLabel, corpusFooter);
    row.setAlignItems(FlexComponent.Alignment.CENTER);
    row.setSpacing(true);
    row.setWidthFull();
    return row;
  }

  /**
   * Row of chips, one per ingested source, with a trailing "Clear all"
   * button. Rebuilt from the registry on every change so the UI cannot
   * drift from the store state.
   */
  private Component buildDocumentChipRow() {
    documentChips.setSpacing(false);
    documentChips.addClassName("doc-chips");

    clearAllButton.addClassName("m06-clear-button");

    HorizontalLayout row = new HorizontalLayout(documentChips, clearAllButton);
    row.setAlignItems(FlexComponent.Alignment.CENTER);
    row.setSpacing(true);
    row.setWidthFull();
    row.setFlexGrow(1, documentChips);
    return row;
  }

  private Component buildAskRow() {
    queryField.setPlaceholder(getTranslation("m06.field.query.placeholder"));
    queryField.addClassName("m06-query-field");
    HorizontalLayout row = new HorizontalLayout(queryField, askButton);
    row.setAlignItems(FlexComponent.Alignment.END);
    row.setSpacing(true);
    return row;
  }

  /**
   * Visible strip of step chips, idle at rest. Grounding is on by
   * product default ({@link ProductConfig#GROUNDING_CHECK_DEFAULT}) so
   * the third chip is shown; if that default ever flips, hide the
   * chip in {@link #onAsk()} or extend the strip builder.
   */
  private Component buildStepsRow() {
    stepRetrieve.addClassNames("step-chip", "idle");
    stepGenerate.addClassNames("step-chip", "idle");
    stepGround.addClassNames("step-chip", "idle");
    stepsRow.setSpacing(true);
    stepsRow.setAlignItems(FlexComponent.Alignment.CENTER);
    return stepsRow;
  }

  private Component buildProgressRow() {
    progress.addClassName("m06-progress");
    progress.setMin(0.0);
    progress.setMax(1.0);
    progress.setValue(0.0);
    progressStatus.addClassName("status-label");
    progressRow.setAlignItems(FlexComponent.Alignment.CENTER);
    progressRow.setSpacing(true);
    progressRow.setVisible(false);
    return progressRow;
  }

  private Component buildMainRow() {
    answerDiv.addClassName("answer-box");
    answerDiv.setWidthFull();

    VerticalLayout left = new VerticalLayout(
        thinkingPanel.component(), new Span(getTranslation("m06.label.answer")), answerDiv);
    left.setPadding(false);
    left.setSpacing(false);
    left.addClassName("m06-answer-column");

    VerticalLayout right = new VerticalLayout(new Span(getTranslation("m06.label.sources")), sourcesPanel);
    right.setPadding(false);
    right.setSpacing(false);
    right.addClassName("m06-sources-column");

    HorizontalLayout row = new HorizontalLayout(left, right);
    row.setWidthFull();
    row.setAlignItems(FlexComponent.Alignment.STRETCH);
    row.setSpacing(true);
    return row;
  }

  // ---------- ingestion (auto on upload) -----------------------------

  /**
   * {@link com.vaadin.flow.server.streams.InMemoryUploadCallback}
   * callback: bytes in, ingestion on a virtual thread, file cleared
   * from the upload widget once done. The callback itself runs on the
   * request thread (session lock held), so we stage the "embedding..."
   * UI state synchronously and hand the actual chunk/embed work off to
   * a background thread -- otherwise the UI would freeze for the whole
   * embedding run (Ollama embed calls are per-chunk and laptop-slow).
   */
  private void ingestUploaded(UploadMetadata metadata, byte[] bytes) {
    String fileName = metadata.fileName();
    if (ragSystem == null) {
      Notification.show(getTranslation("m06.error.not.init"));
      return;
    }

    // Stage the "in progress" UI synchronously: indeterminate bar +
    // explicit status so the user sees that the long embed phase has
    // started rather than staring at a frozen widget.
    String embeddingMsg = getTranslation("m06.status.embedding", fileName);
    askButton.setEnabled(false);
    upload.setDropAllowed(false);
    statusLabel.setText(embeddingMsg);
    statusLabel.removeClassName("finished");
    progress.setIndeterminate(true);
    progressStatus.setText(embeddingMsg);
    progressRow.setVisible(true);

    UI ui = UI.getCurrent();
    String text = new String(bytes, StandardCharsets.UTF_8);

    Thread.ofVirtual().name("m6-ingest-" + System.currentTimeMillis()).start(() -> {
      try {
        IngestionResult result = ragSystem.ingest(fileName, text);
        ui.access(() -> {
          progress.setIndeterminate(false);
          progress.setValue(1.0);
          progressStatus.setText(getTranslation("m06.status.embedded",
              result.sourceName(), result.chunkCount()));
          statusLabel.setText(getTranslation("m06.status.ingested",
              result.sourceName(), result.chunkCount()));
          statusLabel.addClassName("finished");
          refreshCorpusFooter();
          renderDocumentChips();
          // The document now lives in the store and is represented by
          // its chip. Keeping it in the upload widget is redundant, so
          // drop it from the list.
          upload.clearFileList();
          upload.setDropAllowed(true);
          askButton.setEnabled(true);
        });
      } catch (RuntimeException e) {
        logger().warn("Ingest of {} failed: {}", fileName, e.getMessage());
        ui.access(() -> {
          progress.setIndeterminate(false);
          progress.setValue(0.0);
          progressStatus.setText(getTranslation("m06.status.ingest.failed", e.getMessage()));
          statusLabel.setText(getTranslation("m06.status.ingest.error", fileName));
          Notification.show(getTranslation("m06.notify.ingest.error", fileName, e.getMessage()));
          upload.setDropAllowed(true);
          askButton.setEnabled(true);
        });
      }
    });
  }

  /**
   * Rebuilds the document chip strip from {@link RagSystem#listSources()}.
   * Each chip carries a close (x) affordance that drops its source via
   * {@link #removeSource(String)}.
   */
  private void renderDocumentChips() {
    documentChips.removeAll();
    if (ragSystem == null || ragSystem.documentCount() == 0) {
      Span empty = new Span(getTranslation("m06.chip.empty"));
      empty.addClassName("doc-chip-empty");
      documentChips.add(empty);
      clearAllButton.setEnabled(false);
      return;
    }
    for (String sourceName : ragSystem.listSources()) {
      Span chip = new Span();
      chip.addClassName("doc-chip");
      Span label = new Span(sourceName);
      Span close = new Span("\u00D7");
      close.addClassName("doc-chip-close");
      close.getElement().setAttribute("title", getTranslation("m06.chip.remove.title"));
      close.addClickListener(e -> removeSource(sourceName));
      chip.add(label, close);
      documentChips.add(chip);
    }
    clearAllButton.setEnabled(true);
  }

  private void removeSource(String sourceName) {
    if (ragSystem == null) return;
    int removed = ragSystem.removeSource(sourceName);
    statusLabel.setText(getTranslation("m06.status.removed", sourceName, removed));
    statusLabel.removeClassName("finished");
    refreshCorpusFooter();
    renderDocumentChips();
  }

  private void onClearAll() {
    if (ragSystem == null) return;
    if (ragSystem.chunkCount() == 0) {
      Notification.show(getTranslation("m06.notify.nothing.to.clear"));
      return;
    }
    ragSystem.clearAll();
    statusLabel.setText(getTranslation("m06.status.cleared"));
    statusLabel.removeClassName("finished");
    refreshCorpusFooter();
    renderDocumentChips();
  }

  private void refreshCorpusFooter() {
    if (ragSystem == null) {
      corpusFooter.setText(getTranslation("m06.corpus.footer", 0, 0));
      return;
    }
    corpusFooter.setText(getTranslation("m06.corpus.footer",
        ragSystem.documentCount(), ragSystem.chunkCount()));
  }

  // ---------- ask / streaming ----------------------------------------

  private void onAsk() {
    if (ragSystem == null) {
      Notification.show(getTranslation("m06.error.not.init"));
      return;
    }
    String query = queryField.getValue();
    if (query == null || query.isBlank()) {
      Notification.show(getTranslation("m06.error.query.empty"));
      return;
    }
    if (ragSystem.chunkCount() == 0) {
      Notification.show(getTranslation("m06.error.no.docs"));
      return;
    }

    answerDiv.removeAll();
    answerDiv.setText("");
    // Streaming phase: plain text in monospace with pre-wrap; Markdown
    // renders only once the full answer has landed.
    answerDiv.addClassName("streaming");
    sourcesPanel.removeAll();
    thinkingPanel.resetForStreaming();
    askButton.setEnabled(false);
    statusLabel.setText(getTranslation("m06.status.answering"));
    statusLabel.removeClassName("finished");

    resetStepChips();
    // Indeterminate until the first pipeline stage fires. The query
    // embedding + first retrieval tick can take a second on a laptop;
    // a pulsing bar makes "we're working" obvious without pretending
    // to know the fraction.
    progress.setIndeterminate(true);
    progressStatus.setText(getTranslation("m06.status.embedding.query"));
    progressRow.setVisible(true);

    UI ui = UI.getCurrent();
    ThrottledUiBuffer live =
        new ThrottledUiBuffer(ui, 75, answerDiv::setText);
    ThrottledUiBuffer liveThinking =
        new ThrottledUiBuffer(ui, 75, thinkingPanel::showStreaming);

    Thread.ofVirtual().name("m6-ask-" + System.currentTimeMillis()).start(() -> {
      try {
        GeneratedAnswer answer = ragSystem.ask(query,
            token -> {
              live.append(token);
            },
            thinkingToken -> {
              liveThinking.append(thinkingToken);
            },
            stage -> ui.access(() -> applyStage(stage)));
        ui.access(() -> finalise(answer));
      } catch (RuntimeException e) {
        logger().warn("ask failed: {}", e.getMessage());
        ui.access(() -> {
          Notification.show(getTranslation("m06.error.ask", e.getMessage()));
          statusLabel.setText(getTranslation("m06.status.error.detail", e.getMessage()));
          progress.setIndeterminate(false);
          progressStatus.setText(getTranslation("m06.status.error.detail", e.getMessage()));
          askButton.setEnabled(true);
        });
      }
    });
  }

  /**
   * Drives both the step chips and the progress bar from a single
   * {@link RagPipeline.Stage} transition. Each chip moves
   * {@code idle -> active -> done} exactly once per run; the bar gets
   * a weighted fraction per phase so it doesn't freeze at ~60% during
   * the long generation step.
   */
  private void applyStage(RagPipeline.Stage stage) {
    double fraction = switch (stage) {
      case RETRIEVAL_STARTED -> 0.05;
      case RETRIEVAL_FINISHED -> 0.15;
      case GENERATION_STARTED -> 0.20;
      // Grounding is on by default in module 6; if a user's build
      // disables it, GENERATION_FINISHED is the last transition before
      // DONE, so we cap at 0.80 here and let DONE land at 1.0.
      case GENERATION_FINISHED -> 0.80;
      case GROUNDING_STARTED -> 0.85;
      case GROUNDING_FINISHED -> 1.0;
      case DONE -> 1.0;
    };
    String label = switch (stage) {
      case RETRIEVAL_STARTED -> getTranslation("m06.stage.retrieval.started");
      case RETRIEVAL_FINISHED -> getTranslation("m06.stage.retrieval.finished");
      case GENERATION_STARTED -> getTranslation("m06.stage.generation.started");
      case GENERATION_FINISHED -> getTranslation("m06.stage.generation.finished");
      case GROUNDING_STARTED -> getTranslation("m06.stage.grounding.started");
      case GROUNDING_FINISHED -> getTranslation("m06.stage.grounding.finished");
      case DONE -> getTranslation("m06.stage.done");
    };
    // First stage tick flips the bar from indeterminate (query-embed
    // phase) to determinate weighted fractions.
    if (progress.isIndeterminate()) progress.setIndeterminate(false);
    progress.setValue(fraction);
    progressStatus.setText(label);

    switch (stage) {
      case RETRIEVAL_STARTED -> setChip(stepRetrieve, "active");
      case RETRIEVAL_FINISHED -> setChip(stepRetrieve, "done");
      case GENERATION_STARTED -> setChip(stepGenerate, "active");
      case GENERATION_FINISHED -> setChip(stepGenerate, "done");
      case GROUNDING_STARTED -> setChip(stepGround, "active");
      case GROUNDING_FINISHED -> setChip(stepGround, "done");
      case DONE -> {
        // When grounding is disabled or was skipped (e.g. refusal),
        // the ground chip never moved off idle; mark it done so the
        // strip ends in a consistent state.
        if (!stepGround.hasClassName("done")) setChip(stepGround, "done");
      }
      default -> {
        // Exhaustive over the enum; default keeps checkstyle happy.
      }
    }
  }

  private void resetStepChips() {
    setChip(stepRetrieve, "idle");
    setChip(stepGenerate, "idle");
    setChip(stepGround, "idle");
  }

  private static void setChip(Span chip, String state) {
    chip.removeClassNames("idle", "active", "done");
    chip.addClassNames("step-chip", state);
  }

  private void finalise(GeneratedAnswer answer) {
    // Swap the streaming plain-text view for the Markdown-rendered
    // HTML. commonmark escapes stray HTML for us; the renderer is
    // configured for GFM pipe tables and sanitised link URLs.
    String html = MarkdownSupport.renderSafeHtml(answer.text());
    answerDiv.removeAll();
    answerDiv.setText("");
    answerDiv.removeClassName("streaming");
    answerDiv.add(MarkdownSupport.htmlDiv(html));

    sourcesPanel.renderProductSources(answer.usedHits());
    finaliseThinking(answer.thinking());
    // "Finished" is a distinct terminal state from a running "Done."
    // glimpse: green text + checkmark so the user sees unambiguously
    // that the whole pipeline has come to rest.
    String finished = getTranslation("m06.status.finished");
    statusLabel.setText(finished);
    statusLabel.addClassName("finished");
    progressStatus.setText(finished);
    if (progress.isIndeterminate()) progress.setIndeterminate(false);
    progress.setValue(1.0);
    askButton.setEnabled(true);
  }

  private void finaliseThinking(String thinking) {
    if (thinking == null || thinking.isEmpty()) {
      thinkingPanel.finalise(thinking, "");
      return;
    }
    // Swap the raw-token streaming view for the Markdown-rendered
    // HTML, the same treatment the answer box gets. commonmark escapes
    // stray HTML; renderer is configured for GFM tables and sanitised
    // links.
    String html = MarkdownSupport.renderSafeHtml(thinking);
    thinkingPanel.finalise(thinking, html);
  }

}
