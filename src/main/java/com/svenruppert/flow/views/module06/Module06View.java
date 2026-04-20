package com.svenruppert.flow.views.module06;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.views.help.ExpandableHelp;
import com.svenruppert.flow.views.help.ParameterDocs;
import com.svenruppert.flow.views.module01.DefaultLlmClient;
import com.svenruppert.flow.views.module01.LlmClient;
import com.svenruppert.flow.views.module01.LlmConfig;
import com.svenruppert.flow.views.module02.EclipseStoreJVectorStore;
import com.svenruppert.flow.views.module03.SentenceChunker;
import com.svenruppert.flow.views.module04.RetrievalHit;
import com.svenruppert.flow.views.module05.GeneratedAnswer;
import com.svenruppert.flow.views.module05.OllamaStreamingApi;
import com.svenruppert.flow.views.module05.RagPipeline;
import com.svenruppert.flow.views.module05.SimpleGroundedPromptTemplate;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.details.Details;
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
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
@Route(value = Module06View.PATH, layout = MainLayout.class)
public class Module06View
    extends VerticalLayout
    implements HasLogger {

  public static final String PATH = "Module06";

  /**
   * Shared Markdown pipeline -- same shape as Module 5 so a reader
   * sees one rendering story across the workshop. GFM pipe tables
   * enabled, raw HTML escaped, link URLs sanitised.
   */
  private static final Set<Extension> MARKDOWN_EXTENSIONS =
      Set.of(TablesExtension.create());
  private static final Parser MARKDOWN_PARSER = Parser.builder()
      .extensions(MARKDOWN_EXTENSIONS).build();
  private static final HtmlRenderer MARKDOWN_RENDERER = HtmlRenderer.builder()
      .extensions(MARKDOWN_EXTENSIONS)
      .escapeHtml(true)
      .sanitizeUrls(true)
      .build();

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
  private final Span statusLabel = new Span("No documents ingested yet.");
  private final Paragraph corpusFooter = new Paragraph("0 documents, 0 chunks");

  /**
   * One pill per ingested source. Clicking the pill's close affordance
   * drops that source from the store. Strip is rebuilt from
   * {@link RagSystem#listSources()} after every ingest or remove, so a
   * stale UI cache can't diverge from the real registry.
   */
  private final HorizontalLayout documentChips = new HorizontalLayout();
  private final Button clearAllButton = new Button("Clear all");

  private final TextField queryField = new TextField();
  private final Button askButton = new Button("Ask");
  private final Div answerDiv = new Div();
  private final VerticalLayout sourcesPanel = new VerticalLayout();

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
  private final Span stepRetrieve = new Span("1. Retrieve");
  private final Span stepGenerate = new Span("2. Generate");
  private final Span stepGround = new Span("3. Check grounding");
  private final ProgressBar progress = new ProgressBar();
  private final Span progressStatus = new Span();
  private final HorizontalLayout stepsRow = new HorizontalLayout(
      stepRetrieve, stepGenerate, stepGround);
  private final HorizontalLayout progressRow = new HorizontalLayout(
      progress, progressStatus);

  // ------- thinking panel (side channel) -----------------------------

  private final Div thinkingDiv = new Div();
  private final Details thinkingDetails = new Details("Thinking", thinkingDiv);

  public Module06View() {
    this(new DefaultLlmClient(LlmConfig.defaults()), LlmConfig.defaults());
  }

  public Module06View(LlmClient llmClient, LlmConfig llmConfig) {
    this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
    this.llmConfig = Objects.requireNonNull(llmConfig, "llmConfig");

    setSizeFull();
    setPadding(true);
    setSpacing(true);

    add(buildStyleBlock());
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
      Notification.show("Could not initialise: " + e.getMessage());
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

  private Component buildStyleBlock() {
    String css = """
            <style>
              .answer-box { font-size: 0.95rem; line-height: 1.55;
                            padding: 0.8em; background: #fafafa;
                            border: 1px solid #ddd; border-radius: 4px;
                            min-height: 12em; }
              /* While streaming the answer is plain text; preserve
                 new-lines with monospace + pre-wrap. finaliseAnswer
                 drops this class and renders Markdown. */
              .answer-box.streaming { font-family: ui-monospace, SFMono-Regular, monospace;
                                      white-space: pre-wrap; }
              .answer-box p:first-child { margin-top: 0; }
              .answer-box p:last-child { margin-bottom: 0; }
              .answer-box pre { background: #f3f3f3; padding: 0.6em 0.8em;
                                border-radius: 4px; overflow-x: auto; }
              .answer-box code { font-family: ui-monospace, SFMono-Regular, monospace; }
              .answer-box table { border-collapse: collapse; margin: 0.6em 0; }
              .answer-box th, .answer-box td { border: 1px solid #ccc;
                                               padding: 0.3em 0.6em; }
              .answer-box th { background: #eee; }
              .source-item { border-left: 4px solid #2e7d32;
                             padding: 0.4em 0.6em; margin-bottom: 0.4em;
                             background: #f1f8f1;
                             border-top: 1px solid #eee;
                             border-right: 1px solid #eee;
                             border-bottom: 1px solid #eee; }
              .source-label { font-family: ui-monospace, SFMono-Regular, monospace;
                              font-size: 0.75rem; color: #2e7d32;
                              font-weight: 600; }
              .source-preview { font-size: 0.85rem; color: #333; margin-top: 0.2em; }
              .status-label { font-family: ui-monospace, SFMono-Regular, monospace;
                              font-size: 0.85rem; color: #555; }
              /* Finished state for the status label and the progress
                 status: green + check so the "process is done" signal
                 is unmistakable even on a laptop-speed run that just
                 finished a long generation. */
              .status-label.finished {
                color: #1b5e20; font-weight: 600;
              }
              .status-label.finished::before { content: "\\2713\\00a0"; }
              /* Document chip: pill per ingested source with a close (x)
                 affordance. Clicking x removes that source from the
                 store. */
              .doc-chip {
                font-family: ui-monospace, SFMono-Regular, monospace;
                font-size: 0.8rem;
                padding: 0.15em 0.2em 0.15em 0.7em;
                border-radius: 999px;
                background: #e3f2fd;
                color: #0d47a1;
                border: 1px solid #90caf9;
                display: inline-flex; align-items: center; gap: 0.3em;
              }
              .doc-chip-close {
                display: inline-flex; align-items: center;
                justify-content: center;
                width: 1.2em; height: 1.2em;
                border-radius: 50%;
                cursor: pointer;
                color: #0d47a1;
              }
              .doc-chip-close:hover { background: #bbdefb; }
              /* Model chip in the header: shows which embedder and which
                 generation model the product is wired to. Neutral palette
                 so it reads as metadata, not an actionable control. */
              .model-chip {
                font-family: ui-monospace, SFMono-Regular, monospace;
                font-size: 0.75rem;
                padding: 0.15em 0.65em;
                border-radius: 999px;
                background: #f3f3f3;
                color: #333;
                border: 1px solid #ddd;
              }
              .doc-chip-empty {
                font-family: ui-monospace, SFMono-Regular, monospace;
                font-size: 0.8rem; color: #888; font-style: italic;
              }
              /* Step chips: pill per pipeline phase, with idle / active /
                 done states. Active pulses so the user can see that
                 something is happening even during the long generation
                 step. */
              .step-chip {
                font-family: ui-monospace, SFMono-Regular, monospace;
                font-size: 0.8rem;
                padding: 0.2em 0.8em;
                border-radius: 999px;
                border: 1px solid transparent;
                display: inline-flex; align-items: center; gap: 0.35em;
              }
              .step-chip.idle {
                background: #eee; color: #888; border-color: #ddd;
              }
              .step-chip.active {
                background: #e3f2fd; color: #0d47a1; border-color: #90caf9;
                animation: m6-pulse 1.1s ease-in-out infinite;
              }
              .step-chip.done {
                background: #e8f5e9; color: #1b5e20; border-color: #a5d6a7;
              }
              .step-chip.done::before { content: "\\2713"; font-weight: 700; }
              .step-chip.active::before { content: "\\2022"; font-weight: 700; }
              .step-chip.idle::before { content: "\\25CB"; }
              @keyframes m6-pulse {
                0%, 100% { box-shadow: 0 0 0 0 rgba(13,71,161,0.25); }
                50%      { box-shadow: 0 0 0 6px rgba(13,71,161,0);    }
              }
              /* Thinking panel: muted palette, monospace, clearly
                 separated from the primary answer. Same look as Module 5
                 so readers recognise it across the workshop. */
              .thinking-details { margin-bottom: 0.4em; }
              .thinking-details::part(summary) {
                color: #555; font-size: 0.85rem; font-style: italic;
              }
              .thinking-box {
                font-size: 0.85rem; line-height: 1.55;
                color: #555; background: #f5f2ea;
                border: 1px dashed #d8cfb6;
                border-radius: 4px;
                padding: 0.6em 0.8em;
                max-height: 20em; overflow-y: auto;
              }
              /* Streaming-phase look: monospace + pre-wrap so raw
                 tokens land cleanly as they arrive. Dropped on
                 finalise, when the whole thinking text is rendered as
                 Markdown. */
              .thinking-box.streaming {
                font-family: ui-monospace, SFMono-Regular, monospace;
                font-size: 0.8rem;
                white-space: pre-wrap;
              }
              .thinking-box p:first-child { margin-top: 0; }
              .thinking-box p:last-child { margin-bottom: 0; }
              .thinking-box pre { background: #efeadf; padding: 0.5em 0.7em;
                                  border-radius: 4px; overflow-x: auto; }
              .thinking-box code { font-family: ui-monospace, SFMono-Regular, monospace; }
            </style>
            """;
    return new Html(css);
  }

  private Component buildHeader() {
    H3 title = new H3("Module 6 -- RAG Product");
    Paragraph subtitle = new Paragraph(
        "Drop a document; it is ingested on arrival. Type a question; "
            + "tokens stream in as the model produces them. "
            + "No knobs to tune -- the product has picked defaults.");
    subtitle.getStyle().set("color", "#666").set("margin-top", "-0.4em");

    // Product defaults are opaque to the user by design, but which
    // embedder and which generation model are in use matters for
    // troubleshooting ("why is this slow?", "why doesn't it know X?").
    // Surface them as monospace chips in the header.
    Span embedderChip = new Span("embedder: " + ProductConfig.DEFAULT_EMBEDDING_MODEL);
    Span generatorChip = new Span("generator: " + ProductConfig.DEFAULT_GENERATION_MODEL);
    embedderChip.addClassName("model-chip");
    generatorChip.addClassName("model-chip");
    HorizontalLayout modelRow = new HorizontalLayout(embedderChip, generatorChip);
    modelRow.setSpacing(true);
    modelRow.setPadding(false);
    modelRow.getStyle().set("margin-top", "-0.2em");

    VerticalLayout box = new VerticalLayout(title, subtitle, modelRow);
    box.setPadding(false);
    box.setSpacing(false);
    return box;
  }

  private Component buildCorpusRow() {
    upload.setAcceptedFileTypes("text/plain", "text/markdown", ".txt", ".md", ".markdown");
    upload.setMaxFiles(20);
    upload.setDropLabel(new Span("Drop .txt / .md here"));
    upload.setWidth("18em");
    // Ingestion is wired through the UploadHandler in the field
    // initialiser -- no listener needed here.

    statusLabel.addClassName("status-label");
    corpusFooter.getStyle()
        .set("color", "#555")
        .set("font-family", "ui-monospace, SFMono-Regular, monospace")
        .set("font-size", "0.85rem");

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
    documentChips.getStyle()
        .set("gap", "0.3em")
        .set("flex", "1")
        .set("min-width", "0")
        .set("overflow-x", "auto");

    clearAllButton.getStyle().set("flex-shrink", "0");

    HorizontalLayout row = new HorizontalLayout(documentChips, clearAllButton);
    row.setAlignItems(FlexComponent.Alignment.CENTER);
    row.setSpacing(true);
    row.setWidthFull();
    row.setFlexGrow(1, documentChips);
    return row;
  }

  private Component buildAskRow() {
    queryField.setPlaceholder("Ask a question");
    queryField.setWidth("32em");
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
    progress.setWidth("22em");
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

    thinkingDiv.addClassName("thinking-box");
    thinkingDetails.addClassName("thinking-details");
    thinkingDetails.setOpened(false);
    thinkingDetails.setVisible(false);   // shown only when thinking content arrives

    VerticalLayout left = new VerticalLayout(
        thinkingDetails, new Span("Answer"), answerDiv);
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
      Notification.show("System not initialised yet.");
      return;
    }

    // Stage the "in progress" UI synchronously: indeterminate bar +
    // explicit status so the user sees that the long embed phase has
    // started rather than staring at a frozen widget.
    askButton.setEnabled(false);
    upload.setDropAllowed(false);
    statusLabel.setText("Embedding '" + fileName + "'...");
    statusLabel.removeClassName("finished");
    progress.setIndeterminate(true);
    progressStatus.setText("Embedding '" + fileName + "'...");
    progressRow.setVisible(true);

    UI ui = UI.getCurrent();
    String text = new String(bytes, StandardCharsets.UTF_8);

    Thread.ofVirtual().name("m6-ingest-" + System.currentTimeMillis()).start(() -> {
      try {
        IngestionResult result = ragSystem.ingest(fileName, text);
        ui.access(() -> {
          progress.setIndeterminate(false);
          progress.setValue(1.0);
          progressStatus.setText("Embedded '" + result.sourceName()
              + "' (" + result.chunkCount() + " chunks).");
          statusLabel.setText("Ingested '" + result.sourceName()
              + "' (" + result.chunkCount() + " new chunks).");
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
          progressStatus.setText("Ingest failed: " + e.getMessage());
          statusLabel.setText("Ingest of '" + fileName + "' failed.");
          Notification.show("Could not ingest " + fileName + ": " + e.getMessage());
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
      Span empty = new Span("no documents ingested");
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
      close.getElement().setAttribute("title", "Remove this document");
      close.addClickListener(e -> removeSource(sourceName));
      chip.add(label, close);
      documentChips.add(chip);
    }
    clearAllButton.setEnabled(true);
  }

  private void removeSource(String sourceName) {
    if (ragSystem == null) return;
    int removed = ragSystem.removeSource(sourceName);
    statusLabel.setText("Removed '" + sourceName + "' (" + removed + " chunks).");
    statusLabel.removeClassName("finished");
    refreshCorpusFooter();
    renderDocumentChips();
  }

  private void onClearAll() {
    if (ragSystem == null) return;
    if (ragSystem.chunkCount() == 0) {
      Notification.show("Nothing to clear.");
      return;
    }
    ragSystem.clearAll();
    statusLabel.setText("Cleared all documents.");
    statusLabel.removeClassName("finished");
    refreshCorpusFooter();
    renderDocumentChips();
  }

  private void refreshCorpusFooter() {
    if (ragSystem == null) {
      corpusFooter.setText("0 documents, 0 chunks");
      return;
    }
    corpusFooter.setText(
        ragSystem.documentCount() + " documents, "
            + ragSystem.chunkCount() + " chunks");
  }

  // ---------- ask / streaming ----------------------------------------

  private void onAsk() {
    if (ragSystem == null) {
      Notification.show("System not initialised yet.");
      return;
    }
    String query = queryField.getValue();
    if (query == null || query.isBlank()) {
      Notification.show("Type a question first.");
      return;
    }
    if (ragSystem.chunkCount() == 0) {
      Notification.show("Drop at least one document first.");
      return;
    }

    answerDiv.removeAll();
    answerDiv.setText("");
    // Streaming phase: plain text in monospace with pre-wrap; Markdown
    // renders only once the full answer has landed.
    answerDiv.addClassName("streaming");
    sourcesPanel.removeAll();
    thinkingDiv.removeAll();
    thinkingDiv.setText("");
    thinkingDiv.addClassName("streaming");
    thinkingDetails.setSummaryText("Thinking");
    thinkingDetails.setOpened(false);
    thinkingDetails.setVisible(false);
    askButton.setEnabled(false);
    statusLabel.setText("Answering...");
    statusLabel.removeClassName("finished");

    resetStepChips();
    // Indeterminate until the first pipeline stage fires. The query
    // embedding + first retrieval tick can take a second on a laptop;
    // a pulsing bar makes "we're working" obvious without pretending
    // to know the fraction.
    progress.setIndeterminate(true);
    progressStatus.setText("Embedding query...");
    progressRow.setVisible(true);

    UI ui = UI.getCurrent();
    StringBuilder live = new StringBuilder();
    StringBuilder liveThinking = new StringBuilder();

    Thread.ofVirtual().name("m6-ask-" + System.currentTimeMillis()).start(() -> {
      try {
        GeneratedAnswer answer = ragSystem.ask(query,
            token -> {
              live.append(token);
              String snapshot = live.toString();
              ui.access(() -> answerDiv.setText(snapshot));
            },
            thinkingToken -> {
              liveThinking.append(thinkingToken);
              String snapshot = liveThinking.toString();
              int chars = snapshot.length();
              ui.access(() -> {
                // First thinking token -> reveal the panel. Auto-open
                // so users notice their model is emitting reasoning;
                // they can collapse it manually.
                if (!thinkingDetails.isVisible()) {
                  thinkingDetails.setVisible(true);
                  thinkingDetails.setOpened(true);
                }
                thinkingDiv.setText(snapshot);
                thinkingDetails.setSummaryText(
                    "Thinking (" + chars + " chars)");
              });
            },
            stage -> ui.access(() -> applyStage(stage)));
        ui.access(() -> finalise(answer));
      } catch (RuntimeException e) {
        logger().warn("ask failed: {}", e.getMessage());
        ui.access(() -> {
          Notification.show("Ask failed: " + e.getMessage());
          statusLabel.setText("error: " + e.getMessage());
          progress.setIndeterminate(false);
          progressStatus.setText("error: " + e.getMessage());
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
      case RETRIEVAL_STARTED -> "Retrieving chunks...";
      case RETRIEVAL_FINISHED -> "Retrieval done.";
      case GENERATION_STARTED -> "Generating answer (streaming tokens)...";
      case GENERATION_FINISHED -> "Generation done.";
      case GROUNDING_STARTED -> "Running grounding check...";
      case GROUNDING_FINISHED -> "Grounding check done.";
      case DONE -> "Done.";
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
    String html = MARKDOWN_RENDERER.render(MARKDOWN_PARSER.parse(answer.text()));
    answerDiv.removeAll();
    answerDiv.setText("");
    answerDiv.removeClassName("streaming");
    answerDiv.add(new Html("<div>" + html + "</div>"));

    renderSources(answer.usedHits());
    finaliseThinking(answer.thinking());
    // "Finished" is a distinct terminal state from a running "Done."
    // glimpse: green text + checkmark so the user sees unambiguously
    // that the whole pipeline has come to rest.
    statusLabel.setText("Finished");
    statusLabel.addClassName("finished");
    progressStatus.setText("Finished");
    if (progress.isIndeterminate()) progress.setIndeterminate(false);
    progress.setValue(1.0);
    askButton.setEnabled(true);
  }

  private void finaliseThinking(String thinking) {
    if (thinking == null || thinking.isEmpty()) {
      thinkingDetails.setVisible(false);
      return;
    }
    // Swap the raw-token streaming view for the Markdown-rendered
    // HTML, the same treatment the answer box gets. commonmark escapes
    // stray HTML; renderer is configured for GFM tables and sanitised
    // links.
    String html = MARKDOWN_RENDERER.render(MARKDOWN_PARSER.parse(thinking));
    thinkingDiv.removeAll();
    thinkingDiv.setText("");
    thinkingDiv.removeClassName("streaming");
    thinkingDiv.add(new Html("<div>" + html + "</div>"));
    thinkingDetails.setSummaryText("Thinking (" + thinking.length() + " chars)");
    thinkingDetails.setVisible(true);
    // Collapse once the answer is available; the user can re-open to
    // inspect the reasoning.
    thinkingDetails.setOpened(false);
  }

  private void renderSources(List<RetrievalHit> hits) {
    sourcesPanel.removeAll();
    int number = 1;
    for (RetrievalHit hit : hits) {
      Div item = new Div();
      item.addClassName("source-item");

      Span label = new Span("[Chunk " + number + "]");
      label.addClassName("source-label");

      Div preview = new Div();
      preview.addClassName("source-preview");
      preview.setText(preview(hit.chunk().text(), 140));

      item.add(label, preview);
      sourcesPanel.add(item);
      number++;
    }
  }

  private static String preview(String text, int max) {
    String flat = text.replace('\n', ' ').replace('\r', ' ').trim();
    return flat.length() <= max ? flat : flat.substring(0, max) + "...";
  }
}
