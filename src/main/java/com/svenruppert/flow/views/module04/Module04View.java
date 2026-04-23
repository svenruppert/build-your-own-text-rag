package com.svenruppert.flow.views.module04;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.WorkshopDefaults;
import com.svenruppert.flow.util.AsyncTask;
import com.svenruppert.flow.util.UploadTempDir;
import com.svenruppert.flow.views.help.ExpandableHelp;
import com.svenruppert.flow.views.help.ParameterDocs;
import com.svenruppert.flow.views.help.PendingUploadZone;
import com.svenruppert.flow.views.module01.DefaultLlmClient;
import com.svenruppert.flow.views.module01.LlmClient;
import com.svenruppert.flow.views.module03.Chunk;
import com.svenruppert.flow.views.module03.Document;
import com.svenruppert.flow.views.module03.FileDocumentLoader;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.grid.Grid;
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
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.Route;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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

  /**
   * Bundles the in-memory vector store, Lucene keyword index and
   * {@link IngestionPipeline} wired to a common chunk registry, plus
   * the retriever factories. Owns the Lucene index lifecycle.
   */
  private RetrievalLab lab;
  /** Convenience alias for {@code lab.pipeline()} -- heavily referenced. */
  private IngestionPipeline pipeline;
  private UploadTempDir uploadTempDir;
  private int documentsIngested = 0;

  // ------- widgets ---------------------------------------------------

  /**
   * Composite that owns the compact Upload, the pending-file chip
   * strip and the Ingest button. Constructed in the view constructor
   * where translations are available.
   */
  private PendingUploadZone uploadZone;
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
   * Owns the collapsible "judge thinking" panel and drives the
   * LLM-as-judge rerank pass. Constructed in the view ctor where
   * translations are available.
   */
  private JudgeRerankController judge;

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
    this.uploadZone = new PendingUploadZone(new PendingUploadZone.Labels(
        getTranslation("m04.upload.drop"),
        getTranslation("m04.chip.empty"),
        getTranslation("m04.chip.remove.title"),
        getTranslation("m04.button.ingest")));
    uploadZone.ingestButton().addClickListener(e -> onIngest());
    searchButton.setText(getTranslation("m04.button.search"));
    this.judge = new JudgeRerankController(
        getTranslation("m04.judge.thinking.title"),
        offset -> getTranslation("m04.judge.thinking.chunk", offset),
        shown -> getTranslation("m04.judge.thinking.summary", shown));

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
    add(judge.panel());

    // Default selections and event wiring.
    retrieverGroup.setItems(RetrieverChoice.values());
    retrieverGroup.setItemLabelGenerator(c -> getTranslation(c.labelKey));
    retrieverGroup.setValue(RetrieverChoice.VECTOR);
    retrieverGroup.addValueChangeListener(e -> refreshConditionalParams());

    rerankerGroup.setItems(RerankerChoice.values());
    rerankerGroup.setItemLabelGenerator(c -> getTranslation(c.labelKey));
    rerankerGroup.setValue(RerankerChoice.NONE);
    rerankerGroup.addValueChangeListener(e -> refreshConditionalParams());

    searchButton.addClickListener(e -> onSearch());

    refreshConditionalParams();
  }

  // ---------- lifecycle ----------------------------------------------

  @Override
  protected void onAttach(AttachEvent event) {
    super.onAttach(event);
    try {
      this.lab = RetrievalLab.create(llmClient, EMBEDDING_MODEL);
      this.pipeline = lab.pipeline();
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
      if (lab != null) lab.close();
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
    corpusFooter.addClassName("m04-corpus-footer");
    HorizontalLayout row = new HorizontalLayout(uploadZone, corpusFooter);
    row.setAlignItems(FlexComponent.Alignment.CENTER);
    row.setSpacing(true);
    row.setWidthFull();
    row.setFlexGrow(1, uploadZone);
    return row;
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
    resultsGrid.addColumn(h -> pipeline.idOf(h.chunk()))
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
    if (!uploadZone.hasPending()) {
      Notification.show(getTranslation("m04.error.no.files"));
      return;
    }
    FileDocumentLoader loader = new FileDocumentLoader();
    int before = pipeline.chunkRegistry().size();
    int[] processed = {0};
    uploadZone.drain(
        (fileName, bytes) -> {
          Path written = uploadTempDir.resolve(fileName);
          Files.write(written, bytes);
          Document doc = loader.load(written);
          pipeline.ingest(doc);
          documentsIngested++;
          processed[0]++;
        },
        (fileName, msg) -> {
          logger().warn("Ingest of {} failed: {}", fileName, msg);
          Notification.show(getTranslation("m04.error.ingest", fileName, msg));
        });
    int added = pipeline.chunkRegistry().size() - before;
    Notification.show(getTranslation("m04.notify.ingested", processed[0], added));
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
    judge.reset();
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

    AsyncTask.runInBackground(ui, "m4-search",
        () -> runSearchAsync(ui, query, topK, firstStageK, retriever,
            rerankerActive, judgeModel),
        e -> {
          logger().warn("Search failed: {}", e.getMessage(), e);
          Notification.show(getTranslation("m04.error.search", e.getMessage()));
          latencyLabel.setText(getTranslation("m04.status.error"));
          rerankStatus.setText(getTranslation("m04.status.error.detail", e.getMessage()));
          searchButton.setEnabled(true);
        });
  }

  /**
   * Virtual-thread body for {@link #onSearch()}. Every UI touch goes
   * through {@link UI#access(com.vaadin.flow.server.Command)} so Vaadin
   * can push updates incrementally rather than batching them at the
   * end of a long synchronous event. Exception handling is delegated
   * to {@link AsyncTask#runInBackground}.
   */
  private void runSearchAsync(UI ui, String query, int topK, int firstStageK,
                              Retriever retriever, boolean rerankerActive,
                              String judgeModel) {
    long t0 = System.nanoTime();
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
  }

  /**
   * Runs the LLM-as-judge rerank while feeding progress back into the
   * UI via {@link UI#access}. The thinking panel is owned by
   * {@link JudgeRerankController}; this method only supplies the
   * progress observer for the view's own progress bar + status label.
   */
  private List<RetrievalHit> rerankWithProgress(UI ui, String query,
                                                List<RetrievalHit> firstStageHits,
                                                int topK, String judgeModel) {
    return judge.rerank(ui, llmClient, judgeModel, query, firstStageHits, topK,
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
    int candidateK = Math.max(valueOr(topKField, 5) * 2, 10);
    return switch (choice) {
      case VECTOR -> lab.vectorRetriever();
      case BM25 -> lab.bm25Retriever();
      case HYBRID_RRF -> lab.hybridRetriever(
          new FusionStrategy.ReciprocalRankFusion(valueOr(rrfKField, 60.0)),
          candidateK);
      case HYBRID_WEIGHTED -> lab.hybridRetriever(
          new FusionStrategy.WeightedScoreFusion(
              valueOr(vectorWeightField, 0.6),
              valueOr(bm25WeightField, 0.4)),
          candidateK);
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

  private static String preview(String text, int max) {
    String flat = text.replace('\n', ' ').replace('\r', ' ').trim();
    return flat.length() <= max ? flat : flat.substring(0, max) + "...";
  }

}
