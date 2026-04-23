package com.svenruppert.flow.views.module04;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.WorkshopDefaults;
import com.svenruppert.flow.util.UploadTempDir;
import com.svenruppert.flow.views.help.ExpandableHelp;
import com.svenruppert.flow.views.help.ParameterDocs;
import com.svenruppert.flow.views.module01.DefaultLlmClient;
import com.svenruppert.flow.views.module01.LlmClient;
import com.svenruppert.flow.views.module02.DefaultSimilarity;
import com.svenruppert.flow.views.module02.InMemoryVectorStore;
import com.svenruppert.flow.views.module03.Chunk;
import com.svenruppert.flow.views.module03.Document;
import com.svenruppert.flow.views.module03.FileDocumentLoader;
import com.svenruppert.flow.views.module03.SentenceChunker;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.grid.Grid;
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
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.UploadHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
@CssImport("./styles/module04-view.css")
public class Module04View
    extends VerticalLayout
    implements HasLogger {

  public static final String PATH = "Module04";

  private static final String EMBEDDING_MODEL =
      WorkshopDefaults.DEFAULT_EMBEDDING_MODEL;

  private static final Map<String, String> SOURCE_COLOURS = Map.of(
      "vector", "#1976d2",
      "bm25", "#ef6c00",
      "hybrid", "#7b1fa2",
      "llm-judge-reranked", "#2e7d32");

  // Retriever / reranker choice enums drive the radio groups; their
  // labelKey values are i18n keys resolved at runtime.
  enum RetrieverChoice {
    VECTOR("m04.retriever.vector"), BM25("m04.retriever.bm25"),
    HYBRID_RRF("m04.retriever.hybrid.rrf"), HYBRID_WEIGHTED("m04.retriever.hybrid.weighted");
    final String labelKey;

    RetrieverChoice(String labelKey) {
      this.labelKey = labelKey;
    }
  }

  enum RerankerChoice {
    NONE("m04.reranker.none"),
    LLM_JUDGE("m04.reranker.llm.judge");
    final String labelKey;

    RerankerChoice(String labelKey) {
      this.labelKey = labelKey;
    }
  }

  // ------- dependencies ----------------------------------------------

  private final LlmClient llmClient;

  // ------- session-scoped infrastructure ------------------------------

  private InMemoryVectorStore vectorStore;
  private LuceneBM25KeywordIndex keywordIndex;
  private IngestionPipeline pipeline;
  private UploadTempDir uploadTempDir;
  private int documentsIngested = 0;

  // ------- widgets ---------------------------------------------------

  /**
   * File names that have been uploaded but not yet ingested. We track
   * the "still pending" subset ourselves to avoid re-ingesting on every
   * click.
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
  private final Paragraph corpusFooter = new Paragraph();

  private final TextField queryField = new TextField();
  private final IntegerField topKField = sizeField(5);
  private final RadioButtonGroup<RetrieverChoice> retrieverGroup = new RadioButtonGroup<>();
  private final RadioButtonGroup<RerankerChoice> rerankerGroup = new RadioButtonGroup<>();

  private final NumberField rrfKField = doubleField(60.0);
  private final NumberField vectorWeightField = doubleField(0.6);
  private final NumberField bm25WeightField = doubleField(0.4);

  private final ComboBox<String> llmJudgeModel = new ComboBox<>();
  private final IntegerField firstStageKField = sizeField(20);

  private final HorizontalLayout hybridParamsRow = new HorizontalLayout();
  private final HorizontalLayout rerankerParamsRow = new HorizontalLayout();

  private final Button searchButton = new Button();
  private final Span latencyLabel = new Span();

  /**
   * Progress bar for the LLM-as-judge rerank pass. With reasoning
   * models a single candidate can take tens of seconds, so without a
   * visible progress signal the whole UI looks frozen. The bar is
   * hidden for non-reranker runs and for first-stage-only searches.
   */
  private final ProgressBar rerankProgress = new ProgressBar();
  private final Span rerankStatus = new Span();
  private final HorizontalLayout progressRow = new HorizontalLayout();

  private final Grid<RetrievalHit> resultsGrid = new Grid<>(RetrievalHit.class, false);
  private Grid.Column<RetrievalHit> firstStageScoreColumn;
  private Grid.Column<RetrievalHit> scoreColumn;

  /**
   * Snapshot of the first-stage retrieval scores for the last search,
   * keyed by {@link com.svenruppert.flow.views.module03.Chunk}. The
   * reranker, when active, replaces a hit's {@code score} with its own
   * value -- to keep the original visible alongside we remember it
   * here and look it up at render time.
   */
  private Map<com.svenruppert.flow.views.module03.Chunk, Double> firstStageScores = Map.of();

  /**
   * Thinking captured from the LLM-as-judge reranker during the last
   * search, keyed by the candidate whose reply carried it. Populated
   * via the {@link LlmJudgeReranker}'s observer constructor and read
   * back after rerank completes to drive {@link #judgeThinkingPanel}.
   */
  private final Map<com.svenruppert.flow.views.module03.Chunk, String>
      judgeThinking = new LinkedHashMap<>();
  private final Div judgeThinkingBody = new Div();
  private final Details judgeThinkingPanel = new Details("", judgeThinkingBody);

  public Module04View() {
    this(DefaultLlmClient.withDefaults());
  }

  public Module04View(LlmClient llmClient) {
    this.llmClient = llmClient;

    // Set labels and texts
    queryField.setLabel(getTranslation("m04.field.query"));
    topKField.setLabel(getTranslation("m04.field.top.k"));
    firstStageKField.setLabel(getTranslation("m04.field.first.stage.k"));
    rrfKField.setLabel(getTranslation("m04.field.rrf.k"));
    vectorWeightField.setLabel(getTranslation("m04.field.vector.weight"));
    bm25WeightField.setLabel(getTranslation("m04.field.bm25.weight"));
    llmJudgeModel.setLabel(getTranslation("m04.field.judge.model"));
    ingestButton.setText(getTranslation("m04.button.ingest"));
    searchButton.setText(getTranslation("m04.button.search"));
    judgeThinkingPanel.setSummaryText(getTranslation("m04.judge.thinking.title"));

    // setSizeFull() pins this view to the viewport height, which forces
    // the results grid to share a cramped column with the ingestion and
    // retrieval controls. Width only -- the AppLayout content area
    // scrolls the page so the grid can be tall and the controls stay
    // visible above.
    setWidthFull();
    setPadding(true);
    setSpacing(true);

    add(buildHeader());
    add(buildIngestionZone());
    add(buildRetrievalControls());
    add(buildResultsZone());
    add(buildJudgeThinkingPanel());

    // Default selections and event wiring.
    retrieverGroup.setItems(RetrieverChoice.values());
    retrieverGroup.setItemLabelGenerator(c -> getTranslation(c.labelKey));
    retrieverGroup.setValue(RetrieverChoice.VECTOR);
    retrieverGroup.addValueChangeListener(e -> refreshConditionalParams());

    rerankerGroup.setItems(RerankerChoice.values());
    rerankerGroup.setItemLabelGenerator(c -> getTranslation(c.labelKey));
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
      this.pipeline = new IngestionPipeline(llmClient, EMBEDDING_MODEL,
          new SentenceChunker(400), vectorStore, keywordIndex);
      this.uploadTempDir = UploadTempDir.create("module04-upload-");
    } catch (IOException e) {
      logger().error("Could not initialise retrieval lab", e);
      Notification.show(getTranslation("m04.error.init", e.getMessage()));
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
    if (uploadTempDir != null) uploadTempDir.close(msg -> logger().warn(msg));
    super.onDetach(event);
  }

  // ---------- layout builders ----------------------------------------

  private Component buildHeader() {
    H3 title = new H3(getTranslation("m04.header.title"));
    Paragraph subtitle = new Paragraph(getTranslation("m04.header.subtitle"));
    subtitle.addClassName("m04-header-subtitle");
    VerticalLayout box = new VerticalLayout(title, subtitle);
    box.setPadding(false);
    box.setSpacing(false);
    return box;
  }

  private Component buildIngestionZone() {
    upload.setAcceptedFileTypes("text/plain", "text/markdown", ".txt", ".md", ".markdown");
    upload.setMaxFiles(10);
    // Compact visual: fixed width via CSS, internal file list hidden --
    // we render our own chip row next to it.
    upload.addClassName("compact-upload");
    upload.setDropLabel(new Span(getTranslation("m04.upload.drop")));
    // Pending-chip update is wired through the UploadHandler in the
    // field initialiser; no succeeded-listener needed here.

    pendingChips.addClassName("pending-chips");
    pendingChips.setSpacing(false);
    renderPendingChips();

    corpusFooter.addClassName("m04-corpus-footer");

    HorizontalLayout row = new HorizontalLayout(
        upload, pendingChips, ingestButton, corpusFooter);
    row.setAlignItems(FlexComponent.Alignment.CENTER);
    row.setSpacing(true);
    row.setWidthFull();
    row.setFlexGrow(1, pendingChips);
    return row;
  }

  /**
   * Rebuilds the chip strip from {@link #pendingFilenames}. Each chip
   * is a click target that drops that file from the queue, so a user
   * can deselect a mis-picked upload without having to restart.
   */
  private void renderPendingChips() {
    pendingChips.removeAll();
    if (pendingFilenames.isEmpty()) {
      Span empty = new Span(getTranslation("m04.chip.empty"));
      empty.addClassName("pending-chip-empty");
      pendingChips.add(empty);
      return;
    }
    for (String name : pendingFilenames) {
      Span chip = new Span(name + "  \u00D7");
      chip.addClassName("pending-chip");
      chip.getElement().setAttribute("title", getTranslation("m04.chip.remove.title"));
      chip.addClickListener(e -> {
        pendingFilenames.remove(name);
        renderPendingChips();
      });
      pendingChips.add(chip);
    }
  }

  private Component buildRetrievalControls() {
    queryField.addClassName("m04-query-field");
    topKField.addClassName("m04-top-k-field");
    firstStageKField.addClassName("m04-size-field");

    hybridParamsRow.setSpacing(true);
    hybridParamsRow.setAlignItems(FlexComponent.Alignment.START);

    rerankerParamsRow.setSpacing(true);
    rerankerParamsRow.setAlignItems(FlexComponent.Alignment.START);

    latencyLabel.addClassName("m04-latency-label");

    HorizontalLayout queryRow = new HorizontalLayout(
        queryField,
        ExpandableHelp.pair(topKField, ParameterDocs.M4_TOP_K),
        searchButton, latencyLabel);
    queryRow.setAlignItems(FlexComponent.Alignment.START);
    queryRow.setSpacing(true);
    queryRow.setWidthFull();

    rerankProgress.addClassName("m04-rerank-progress");
    rerankProgress.setMin(0.0);
    rerankProgress.setMax(1.0);
    rerankProgress.setValue(0.0);
    rerankStatus.addClassName("m04-rerank-status");
    progressRow.setAlignItems(FlexComponent.Alignment.CENTER);
    progressRow.setSpacing(true);
    progressRow.add(rerankProgress, rerankStatus);
    progressRow.setVisible(false);

    // Retriever and reranker radio groups sit side-by-side with
    // their help panels directly beneath; the conditional
    // parameter rows (hybridParamsRow, rerankerParamsRow) get
    // their own help via refreshConditionalParams().
    VerticalLayout box = new VerticalLayout(
        queryRow,
        ExpandableHelp.pair(retrieverGroup, ParameterDocs.M4_RETRIEVER_MODE),
        hybridParamsRow,
        ExpandableHelp.pair(rerankerGroup, ParameterDocs.M4_RERANKER),
        rerankerParamsRow,
        progressRow);
    box.setPadding(false);
    box.setSpacing(true);
    box.setWidthFull();
    return box;
  }

  private Component buildResultsZone() {
    resultsGrid.addColumn(new ComponentRenderer<>(this::renderSourcePill))
        .setHeader(getTranslation("m04.grid.col.source")).setAutoWidth(true);
    resultsGrid.addColumn(h -> chunkIdFor(h))
        .setHeader(getTranslation("m04.grid.col.chunk.id")).setAutoWidth(true);
    // "First-stage score" is hidden by default. It is turned on whenever
    // a reranker is selected, so participants can compare the score the
    // first-stage retriever assigned against the reranked score in the
    // main "Score" column side by side.
    firstStageScoreColumn = resultsGrid.addColumn(h -> {
      Double fs = firstStageScores.get(h.chunk());
      return (fs == null) ? "--" : String.format(Locale.ROOT, "%.4f", fs);
    }).setHeader(getTranslation("m04.grid.col.first.stage.score")).setAutoWidth(true);
    firstStageScoreColumn.setVisible(false);
    scoreColumn = resultsGrid.addColumn(h -> String.format(Locale.ROOT, "%.4f", h.score()))
        .setHeader(getTranslation("m04.grid.col.score")).setAutoWidth(true);
    resultsGrid.addColumn(h ->
            String.valueOf(h.chunk().metadata().getOrDefault(Chunk.HEADING_PATH, "")))
        .setHeader(getTranslation("m04.grid.col.heading.path")).setAutoWidth(true);
    resultsGrid.addColumn(h -> preview(h.chunk().text(), 120))
        .setHeader(getTranslation("m04.grid.col.preview")).setFlexGrow(1);
    resultsGrid.setAllRowsVisible(false);
    resultsGrid.setWidthFull();
    resultsGrid.addClassName("m04-results-grid");
    return resultsGrid;
  }

  /**
   * Collapsible panel below the grid showing the judge-LLM's reasoning
   * per candidate. Populated only when the LLM-as-judge reranker runs
   * and the chosen model actually emits {@code <think>...</think>}
   * blocks -- deepseek-r1, qwen3-*thinking*, ... . For a non-thinking
   * model or any other reranker the panel stays hidden.
   */
  private Component buildJudgeThinkingPanel() {
    judgeThinkingBody.addClassName("judge-thinking-body");
    judgeThinkingPanel.addClassName("judge-thinking-panel");
    judgeThinkingPanel.setOpened(false);
    judgeThinkingPanel.setVisible(false);
    return judgeThinkingPanel;
  }

  private Component renderSourcePill(RetrievalHit hit) {
    Span pill = new Span(hit.sourceLabel());
    String colour = SOURCE_COLOURS.getOrDefault(hit.sourceLabel(), "#555");
    pill.addClassName("m04-source-pill");
    pill.getStyle().set("background-color", colour);
    return pill;
  }

  // ---------- conditional-param wiring -------------------------------

  private void refreshConditionalParams() {
    hybridParamsRow.removeAll();
    rerankerParamsRow.removeAll();
    RetrieverChoice r = retrieverGroup.getValue();
    if (r == RetrieverChoice.HYBRID_RRF) {
      hybridParamsRow.add(ExpandableHelp.pair(rrfKField, ParameterDocs.M4_RRF_K));
    } else if (r == RetrieverChoice.HYBRID_WEIGHTED) {
      hybridParamsRow.add(
          ExpandableHelp.pair(vectorWeightField, ParameterDocs.M4_VECTOR_WEIGHT),
          ExpandableHelp.pair(bm25WeightField, ParameterDocs.M4_BM25_WEIGHT));
    }

    RerankerChoice rr = rerankerGroup.getValue();
    if (rr == RerankerChoice.LLM_JUDGE) {
      rerankerParamsRow.add(
          ExpandableHelp.pair(llmJudgeModel, ParameterDocs.M4_JUDGE_MODEL),
          ExpandableHelp.pair(firstStageKField, ParameterDocs.M4_RERANK_INPUT_K));
    }
  }

  // ---------- ingestion ----------------------------------------------

  private void onIngest() {
    if (pipeline == null) {
      Notification.show(getTranslation("m04.error.not.init"));
      return;
    }
    if (pendingFilenames.isEmpty()) {
      Notification.show(getTranslation("m04.error.no.files"));
      return;
    }
    FileDocumentLoader loader = new FileDocumentLoader();
    int before = pipeline.chunkRegistry().size();
    int processed = 0;
    // Drain a snapshot so we can mutate pendingFilenames as we go.
    for (String fileName : List.copyOf(pendingFilenames)) {
      byte[] bytes = pendingBytes.get(fileName);
      if (bytes == null) {
        pendingFilenames.remove(fileName);
        continue;
      }
      try {
        Path written = uploadTempDir.resolve(fileName);
        Files.write(written, bytes);
        Document doc = loader.load(written);
        pipeline.ingest(doc);
        documentsIngested++;
        processed++;
      } catch (IOException e) {
        logger().warn("Ingest of {} failed: {}", fileName, e.getMessage());
        Notification.show(getTranslation("m04.error.ingest", fileName, e.getMessage()));
      } finally {
        pendingFilenames.remove(fileName);
        pendingBytes.remove(fileName);
      }
    }
    renderPendingChips();
    int added = pipeline.chunkRegistry().size() - before;
    Notification.show(getTranslation("m04.notify.ingested", processed, added));
    refreshCorpusFooter();
  }

  private void refreshCorpusFooter() {
    int chunks = pipeline != null ? pipeline.chunkRegistry().size() : 0;
    corpusFooter.setText(getTranslation("m04.corpus.footer", documentsIngested, chunks));
  }

  // ---------- search --------------------------------------------------

  private void onSearch() {
    if (pipeline == null) {
      Notification.show(getTranslation("m04.error.not.init"));
      return;
    }
    String query = queryField.getValue();
    if (query == null || query.isBlank()) {
      Notification.show(getTranslation("m04.error.query.empty"));
      return;
    }
    int topK = valueOr(topKField, 5);
    RetrieverChoice retrieverChoice = retrieverGroup.getValue();
    RerankerChoice rerankerChoice = rerankerGroup.getValue();
    boolean rerankerActive = rerankerChoice == RerankerChoice.LLM_JUDGE;
    int firstStageK = rerankerActive ? valueOr(firstStageKField, 20) : topK;
    String judgeModel = Optional.ofNullable(llmJudgeModel.getValue())
        .orElse(LlmJudgeReranker.DEFAULT_MODEL);

    // Reset per-run UI state. Judge-thinking accumulates during the
    // rerank pass; clear it here so a fresh search starts empty.
    judgeThinking.clear();
    judgeThinkingBody.removeAll();
    judgeThinkingPanel.setVisible(false);
    resultsGrid.setItems(List.of());
    firstStageScoreColumn.setVisible(rerankerActive);
    scoreColumn.setHeader(rerankerActive
        ? getTranslation("m04.grid.col.reranker.score")
        : getTranslation("m04.grid.col.score"));
    searchButton.setEnabled(false);
    latencyLabel.setText(rerankerActive
        ? getTranslation("m04.status.reranking")
        : getTranslation("m04.status.searching"));

    // Progress bar is meaningful for the per-candidate rerank loop.
    // For first-stage-only searches there is nothing to measure
    // incrementally, so the row stays hidden.
    if (rerankerActive) {
      rerankProgress.setValue(0.0);
      rerankStatus.setText(getTranslation("m04.status.retrieving.candidates"));
      progressRow.setVisible(true);
    } else {
      progressRow.setVisible(false);
    }

    UI ui = UI.getCurrent();
    Retriever retriever = buildRetriever(retrieverChoice);

    Thread.ofVirtual().name("m4-search-" + System.nanoTime()).start(() ->
        runSearchAsync(ui, query, topK, firstStageK, retriever,
            rerankerActive, judgeModel));
  }

  /**
   * Virtual-thread body for {@link #onSearch()}. Every UI touch goes
   * through {@link UI#access(com.vaadin.flow.server.Command)} so Vaadin
   * can push updates incrementally rather than batching them at the
   * end of a long synchronous event.
   */
  private void runSearchAsync(UI ui, String query, int topK, int firstStageK,
                              Retriever retriever, boolean rerankerActive,
                              String judgeModel) {
    long t0 = System.nanoTime();
    try {
      List<RetrievalHit> firstStageHits = retriever.retrieve(query, firstStageK);
      Map<com.svenruppert.flow.views.module03.Chunk, Double> fsSnapshot =
          snapshotScores(firstStageHits);

      ui.access(() -> {
        firstStageScores = fsSnapshot;
        if (rerankerActive) {
          rerankStatus.setText(getTranslation("m04.status.reranking.progress",
              0, firstStageHits.size()));
        }
      });

      List<RetrievalHit> displayed;
      if (rerankerActive) {
        displayed = rerankWithProgress(ui, query, firstStageHits, topK, judgeModel);
      } else {
        displayed = firstStageHits.stream().limit(topK).toList();
      }

      double millis = (System.nanoTime() - t0) / 1.0e6;
      List<RetrievalHit> finalHits = displayed.stream()
          .sorted(Comparator.comparingDouble(RetrievalHit::score).reversed())
          .toList();

      ui.access(() -> {
        resultsGrid.setItems(finalHits);
        latencyLabel.setText(getTranslation("m04.latency.format",
            String.format(Locale.ROOT, "%.2f", millis)));
        if (rerankerActive) {
          rerankProgress.setValue(1.0);
          rerankStatus.setText(getTranslation("m04.status.done", finalHits.size()));
        }
        searchButton.setEnabled(true);
      });
    } catch (RuntimeException e) {
      logger().warn("Search failed: {}", e.getMessage(), e);
      ui.access(() -> {
        Notification.show(getTranslation("m04.error.search", e.getMessage()));
        latencyLabel.setText(getTranslation("m04.status.error"));
        rerankStatus.setText(getTranslation("m04.status.error.detail", e.getMessage()));
        searchButton.setEnabled(true);
      });
    }
  }

  /**
   * Runs the LLM-as-judge rerank while feeding progress back into the
   * UI via {@link UI#access}. The reranker's progress observer fires
   * on STARTED/FINISHED per candidate; we update the progress bar,
   * the status text and -- once the candidate's reply is in -- the
   * judge-thinking panel, all while the virtual thread is still
   * iterating.
   */
  private List<RetrievalHit> rerankWithProgress(UI ui, String query,
                                                List<RetrievalHit> firstStageHits,
                                                int topK, String judgeModel) {
    LlmJudgeReranker reranker = new LlmJudgeReranker(
        llmClient, judgeModel,
        (candidate, thinking) -> ui.access(() -> {
          // Observer fires on the virtual thread; hop to the UI
          // thread for the map mutation + panel re-render.
          judgeThinking.put(candidate.chunk(), thinking);
          rebuildJudgeThinkingPanel();
        }),
        (phase, index, total, hit) -> ui.access(() -> {
          int oneBased = index + 1;
          if (phase == LlmJudgeReranker.Phase.STARTED) {
            rerankStatus.setText(getTranslation("m04.status.evaluating",
                oneBased, total, preview(hit.chunk().text(), 60)));
            rerankProgress.setValue(((double) index) / total);
          } else {
            rerankStatus.setText(getTranslation("m04.status.scored",
                oneBased, total,
                String.format(Locale.ROOT, "%.2f", hit.score())));
            rerankProgress.setValue(((double) oneBased) / total);
          }
        }));
    return reranker.rerank(query, firstStageHits, topK);
  }

  /**
   * Renders every captured judge-thinking entry in insertion order.
   * Called incrementally as the reranker finishes each candidate so
   * participants watch the reasoning appear in real time, and once
   * more after the search finishes.
   */
  private void rebuildJudgeThinkingPanel() {
    judgeThinkingBody.removeAll();
    if (judgeThinking.isEmpty()) {
      judgeThinkingPanel.setVisible(false);
      return;
    }
    int shown = 0;
    for (Map.Entry<com.svenruppert.flow.views.module03.Chunk, String> entry
        : judgeThinking.entrySet()) {
      if (entry.getValue() == null || entry.getValue().isEmpty()) continue;
      Div box = new Div();
      box.addClassName("judge-thinking-entry");
      Span heading = new Span(getTranslation("m04.judge.thinking.chunk",
          entry.getKey().startOffset()));
      heading.addClassName("heading");
      box.add(heading, new Span(entry.getValue()));
      judgeThinkingBody.add(box);
      shown++;
    }
    if (shown == 0) {
      judgeThinkingPanel.setVisible(false);
      return;
    }
    judgeThinkingPanel.setSummaryText(
        getTranslation("m04.judge.thinking.summary", shown));
    judgeThinkingPanel.setVisible(true);
    judgeThinkingPanel.setOpened(true);
  }

  private Map<com.svenruppert.flow.views.module03.Chunk, Double>
      snapshotScores(List<RetrievalHit> hits) {
    Map<com.svenruppert.flow.views.module03.Chunk, Double> snapshot = new HashMap<>();
    for (RetrievalHit hit : hits) {
      snapshot.putIfAbsent(hit.chunk(), hit.score());
    }
    return Map.copyOf(snapshot);
  }

  private Retriever buildRetriever(RetrieverChoice choice) {
    VectorRetriever vector = new VectorRetriever(
        llmClient, EMBEDDING_MODEL, vectorStore, pipeline.chunkRegistry());
    BM25Retriever bm25 = new BM25Retriever(keywordIndex, pipeline.chunkRegistry());

    return switch (choice) {
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

  // ---------- population --------------------------------------------

  private void populateModelLists() {
    List<String> models = llmClient.listModels()
        .orElse(List.of(LlmJudgeReranker.DEFAULT_MODEL));
    if (models.isEmpty()) models = List.of(LlmJudgeReranker.DEFAULT_MODEL);
    llmJudgeModel.setItems(models);
    llmJudgeModel.setValue(WorkshopDefaults.preferredGenerationModel(models));
  }

  // ---------- helpers -----------------------------------------------

  private static IntegerField sizeField(int defaultValue) {
    IntegerField field = new IntegerField();
    field.setValue(defaultValue);
    field.setMin(1);
    field.addClassName("m04-size-field");
    return field;
  }

  private static NumberField doubleField(double defaultValue) {
    NumberField field = new NumberField();
    field.setValue(defaultValue);
    field.addClassName("m04-size-field");
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

}
