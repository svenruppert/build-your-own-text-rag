package com.svenruppert.flow.views.module02;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.WorkshopDefaults;
import com.svenruppert.flow.util.PathCleanup;
import com.svenruppert.flow.views.help.ExpandableHelp;
import com.svenruppert.flow.views.help.ParameterDocs;
import com.svenruppert.flow.views.module01.DefaultLlmClient;
import com.svenruppert.flow.views.module01.LlmClient;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.grid.Grid;
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
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Vector Store Lab -- the user-facing half of module 2.
 *
 * <p>Holds two fresh stores side by side: an {@link InMemoryVectorStore}
 * (linear scan) and an {@link EclipseStoreJVectorStore} (persistent
 * raw vectors, in-memory HNSW index). Every add populates <em>both</em>
 * stores so the runtime toggle always switches to a consistent corpus.
 * Every search goes to <em>both</em> stores so the timing strip can
 * show how they scale differently.
 *
 * <p>The didactic message is "swap the implementation at runtime -- the
 * contract is identical": participants see two very different engines
 * answering the same questions, over the same data, with the same API.
 *
 * <p>Each UI session gets its own temporary EclipseStore directory
 * under {@link Files#createTempDirectory(String, java.nio.file.attribute.FileAttribute...)};
 * the directory is torn down on detach. Real cross-session persistence
 * is a separate concern discussed in the workshop's closing block.
 */
@Route(value = Module02View.PATH, layout = MainLayout.class)
@CssImport("./styles/module02-view.css")
public class Module02View
    extends VerticalLayout
    implements HasLogger {

  public static final String PATH = "Module02";

  private static final String FALLBACK_EMBEDDING_MODEL =
      WorkshopDefaults.DEFAULT_EMBEDDING_MODEL;

  private final LlmClient llmClient;

  private final ComboBox<String> modelSelector = new ComboBox<>();
  private final RadioButtonGroup<StoreChoice> activeStoreToggle = new RadioButtonGroup<>();
  private final TextField addTextField = new TextField();
  private final TextField addIdField = new TextField();
  private final TextField addPayloadField = new TextField();
  private final TextArea bulkField = new TextArea();
  private final TextField queryField = new TextField();
  private final IntegerField topKField = new IntegerField();
  private final Grid<SearchHit> hitsGrid = new Grid<>(SearchHit.class, false);
  private final Span timingLabel = new Span();
  private final Span sizeFooter = new Span();

  private InMemoryVectorStore inMemoryStore;
  private EclipseStoreJVectorStore jvectorStore;
  private Path jvectorStorageDir;

  public Module02View() {
    this(DefaultLlmClient.withDefaults());
  }

  public Module02View(LlmClient llmClient) {
    this.llmClient = llmClient;

    modelSelector.setLabel(getTranslation("m02.model.label"));
    addTextField.setLabel(getTranslation("m02.add.text.label"));
    addIdField.setLabel(getTranslation("m02.add.id.label"));
    addPayloadField.setLabel(getTranslation("m02.add.payload.label"));
    bulkField.setLabel(getTranslation("m02.bulk.label"));
    queryField.setLabel(getTranslation("m02.query.label"));
    topKField.setLabel(getTranslation("m02.topk.label"));
    sizeFooter.addClassName("m02-size-footer");

    // setSizeFull() would pin this view to the viewport height, so
    // anything past the bottom gets clipped instead of scrolling. Width
    // only -- AppLayout's content area scrolls the page natively.
    setWidthFull();
    setPadding(true);
    setSpacing(true);

    add(buildHeader());
    add(buildControlsRow());
    add(buildAddRow());
    add(buildQueryRow());
    add(buildResultsSection());
    add(sizeFooter);

    topKField.setValue(5);
    topKField.setMin(1);

    activeStoreToggle.setItems(StoreChoice.IN_MEMORY, StoreChoice.JVECTOR);
    activeStoreToggle.setItemLabelGenerator(choice -> getTranslation(choice.labelKey()));
    activeStoreToggle.setValue(StoreChoice.IN_MEMORY);
    activeStoreToggle.addValueChangeListener(event -> refreshSizeFooter());
  }

  // ---------- lifecycle -----------------------------------------------

  @Override
  protected void onAttach(AttachEvent attachEvent) {
    super.onAttach(attachEvent);
    try {
      this.jvectorStorageDir =
          Files.createTempDirectory("module02-jvector-store-");
      this.jvectorStore = new EclipseStoreJVectorStore(jvectorStorageDir);
      this.inMemoryStore = new InMemoryVectorStore(new DefaultSimilarity());
    } catch (IOException e) {
      logger().error("Could not create temp directory for EclipseStoreJVectorStore", e);
      Notification.show(getTranslation("m02.error.storage", e.getMessage()));
    }
    populateModels();
    refreshSizeFooter();
  }

  @Override
  protected void onDetach(DetachEvent detachEvent) {
    try {
      if (jvectorStore != null) jvectorStore.close();
    } catch (Exception e) {
      logger().warn("jvectorStore close failed: {}", e.getMessage());
    } finally {
      if (inMemoryStore != null) {
        inMemoryStore.clear();
      }
      PathCleanup.deleteRecursively(jvectorStorageDir, msg -> logger().warn(msg));
    }
    super.onDetach(detachEvent);
  }

  // ---------- layout --------------------------------------------------

  private Component buildHeader() {
    H3 title = new H3(getTranslation("m02.header.title"));
    Paragraph subtitle = new Paragraph(getTranslation("m02.header.subtitle"));
    subtitle.addClassName("m02-header-subtitle");
    VerticalLayout box = new VerticalLayout(title, subtitle);
    box.setPadding(false);
    box.setSpacing(false);
    return box;
  }

  private Component buildControlsRow() {
    modelSelector.addClassName("m02-model-selector");
    activeStoreToggle.addClassName("m02-store-toggle");

    HorizontalLayout row = new HorizontalLayout(
        ExpandableHelp.pair(modelSelector, ParameterDocs.M2_EMBEDDING_MODEL),
        ExpandableHelp.pair(activeStoreToggle, ParameterDocs.M2_ACTIVE_STORE));
    row.setAlignItems(FlexComponent.Alignment.START);
    row.setSpacing(true);
    row.setWidthFull();
    return row;
  }

  private Component buildAddRow() {
    addTextField.addClassName("m02-add-text-field");
    addIdField.addClassName("m02-add-id-field");
    addPayloadField.addClassName("m02-add-payload-field");

    Button addButton = new Button(getTranslation("m02.add.button"), event -> onAdd());

    // ID and payload get inline help because participants routinely
    // guess at them; the text field is self-explanatory and stays bare.
    HorizontalLayout singleRow = new HorizontalLayout(
        addTextField,
        ExpandableHelp.pair(addIdField, ParameterDocs.M2_ADD_ID),
        ExpandableHelp.pair(addPayloadField, ParameterDocs.M2_ADD_PAYLOAD),
        addButton);
    singleRow.setAlignItems(FlexComponent.Alignment.START);
    singleRow.setSpacing(true);
    singleRow.setWidthFull();

    // Bulk input lets participants paste or type a batch of texts in
    // one go; each non-blank line becomes its own entry with an
    // auto-generated id and the line text as payload. Much faster
    // than round-tripping the single-row form for every example.
    bulkField.setWidthFull();
    bulkField.addClassName("m02-bulk-field");
    bulkField.setPlaceholder(getTranslation("m02.bulk.placeholder"));
    bulkField.setHelperText(getTranslation("m02.bulk.helper"));

    Button bulkAddButton = new Button(getTranslation("m02.bulk.add.button"), event -> onBulkAdd());
    Button clearBulkButton = new Button(getTranslation("m02.bulk.clear.button"), event -> bulkField.clear());

    HorizontalLayout bulkButtonRow = new HorizontalLayout(bulkAddButton, clearBulkButton);
    bulkButtonRow.setSpacing(true);
    bulkButtonRow.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
    bulkButtonRow.setWidthFull();

    // Visible section divider + heading so the bulk area is obviously
    // a second, separate input path rather than an optional footer
    // hidden off-screen.
    Span sectionTitle = new Span(getTranslation("m02.bulk.section.title"));
    sectionTitle.addClassName("m02-section-title");

    Div divider = new Div();
    divider.addClassName("m02-section-divider");

    VerticalLayout bulkBox = new VerticalLayout(
        divider, sectionTitle, bulkField, bulkButtonRow);
    bulkBox.setPadding(false);
    bulkBox.setSpacing(false);
    bulkBox.setWidthFull();

    VerticalLayout box = new VerticalLayout(singleRow, bulkBox);
    box.setPadding(false);
    box.setSpacing(true);
    box.setWidthFull();
    return box;
  }

  private Component buildQueryRow() {
    queryField.addClassName("m02-query-field");
    topKField.addClassName("m02-top-k-field");

    Button searchButton = new Button(getTranslation("m02.search.button"), event -> onSearch());

    HorizontalLayout row = new HorizontalLayout(
        queryField,
        ExpandableHelp.pair(topKField, ParameterDocs.M2_TOP_K),
        searchButton);
    row.setAlignItems(FlexComponent.Alignment.START);
    row.setSpacing(true);
    row.setWidthFull();
    return row;
  }

  private Component buildResultsSection() {
    hitsGrid.addColumn(SearchHit::id).setHeader(getTranslation("m02.grid.col.id")).setAutoWidth(true);
    hitsGrid.addColumn(h -> String.format(Locale.ROOT, "%.4f", h.score()))
        .setHeader(getTranslation("m02.grid.col.score")).setAutoWidth(true);
    hitsGrid.addColumn(SearchHit::payload).setHeader(getTranslation("m02.grid.col.payload")).setFlexGrow(1);
    hitsGrid.setAllRowsVisible(true);
    hitsGrid.setWidthFull();

    timingLabel.addClassName("m02-timing-label");

    VerticalLayout box = new VerticalLayout(hitsGrid, timingLabel);
    box.setPadding(false);
    box.setSpacing(false);
    box.setWidthFull();
    return box;
  }

  // ---------- behaviour -----------------------------------------------

  private void populateModels() {
    List<String> names = llmClient.listModels().orElse(List.of());
    if (names.isEmpty()) {
      names = List.of(FALLBACK_EMBEDDING_MODEL);
      Notification.show(getTranslation("m02.model.fallback", FALLBACK_EMBEDDING_MODEL));
    }
    modelSelector.setItems(names);
    modelSelector.setValue(WorkshopDefaults.preferredEmbeddingModel(names));
  }

  private void onAdd() {
    if (inMemoryStore == null || jvectorStore == null) {
      Notification.show(getTranslation("m02.error.not.init"));
      return;
    }
    String text = addTextField.getValue();
    if (text == null || text.isBlank()) {
      Notification.show(getTranslation("m02.add.empty"));
      return;
    }
    String model = resolveModel();
    Optional<float[]> maybeVec = llmClient.embed(text, model);
    if (maybeVec.isEmpty()) {
      Notification.show(getTranslation("m02.embed.failed"));
      return;
    }
    float[] vector = maybeVec.get();
    String id = blankOr(addIdField.getValue())
        .orElse("auto-" + UUID.randomUUID().toString().substring(0, 8));
    String payload = blankOr(addPayloadField.getValue()).orElse(text);

    try {
      inMemoryStore.add(id, vector, payload);
      jvectorStore.add(id, vector, payload);
    } catch (IllegalArgumentException e) {
      // Mixed dimensions: the stores reject the add because the corpus
      // already uses a different embedding model. Guide the user.
      Notification.show(getTranslation("m02.add.rejected", e.getMessage()));
      return;
    }

    addTextField.clear();
    addIdField.clear();
    addPayloadField.clear();
    refreshSizeFooter();
  }

  /**
   * Embeds each non-blank line in {@link #bulkField} and inserts it
   * into both stores. Failures (embedding error, dimension clash)
   * are counted and surfaced in a single summary notification so a
   * partial batch still produces a useful amount of corpus.
   */
  private void onBulkAdd() {
    if (inMemoryStore == null || jvectorStore == null) {
      Notification.show(getTranslation("m02.error.not.init"));
      return;
    }
    String raw = bulkField.getValue();
    if (raw == null || raw.isBlank()) {
      Notification.show(getTranslation("m02.bulk.empty"));
      return;
    }
    List<String> lines = raw.lines()
        .map(String::strip)
        .filter(s -> !s.isEmpty())
        .toList();
    if (lines.isEmpty()) {
      Notification.show(getTranslation("m02.bulk.all.blank"));
      return;
    }

    String model = resolveModel();
    int added = 0;
    int embedFailed = 0;
    int rejected = 0;
    for (String line : lines) {
      Optional<float[]> maybeVec = llmClient.embed(line, model);
      if (maybeVec.isEmpty()) {
        embedFailed++;
        continue;
      }
      String id = "auto-" + UUID.randomUUID().toString().substring(0, 8);
      try {
        inMemoryStore.add(id, maybeVec.get(), line);
        jvectorStore.add(id, maybeVec.get(), line);
        added++;
      } catch (IllegalArgumentException e) {
        // Most likely a dimension clash: once the first vector fixes
        // the corpus dimension, later ones with a different model
        // cannot be mixed in. Stop early -- the message would repeat.
        rejected++;
        logger().warn("Bulk add rejected line '{}': {}", line, e.getMessage());
        break;
      }
    }

    String addedPart = getTranslation("m02.bulk.added", added);
    String failedPart = embedFailed > 0
        ? ", " + getTranslation("m02.bulk.embed.failed", embedFailed) : "";
    String rejectedPart = rejected > 0
        ? ", " + getTranslation("m02.bulk.rejected", rejected) : "";
    Notification.show(getTranslation("m02.bulk.prefix") + ": " + addedPart + failedPart + rejectedPart);

    if (added > 0 && embedFailed == 0 && rejected == 0) {
      bulkField.clear();
    }
    refreshSizeFooter();
  }

  private void onSearch() {
    if (inMemoryStore == null || jvectorStore == null) {
      Notification.show(getTranslation("m02.error.not.init"));
      return;
    }
    String text = queryField.getValue();
    if (text == null || text.isBlank()) {
      Notification.show(getTranslation("m02.query.empty"));
      return;
    }
    Integer k = topKField.getValue();
    if (k == null || k <= 0) k = 5;

    String model = resolveModel();
    Optional<float[]> maybeVec = llmClient.embed(text, model);
    if (maybeVec.isEmpty()) {
      Notification.show(getTranslation("m02.query.embed.failed"));
      return;
    }
    float[] query = maybeVec.get();

    // Search both stores, time both.
    long t0 = System.nanoTime();
    List<SearchHit> inMemoryHits;
    try {
      inMemoryHits = inMemoryStore.search(query, k);
    } catch (IllegalArgumentException e) {
      Notification.show(getTranslation("m02.search.rejected.inmemory", e.getMessage()));
      return;
    }
    long t1 = System.nanoTime();
    List<SearchHit> jvectorHits;
    try {
      jvectorHits = jvectorStore.search(query, k);
    } catch (IllegalArgumentException e) {
      Notification.show(getTranslation("m02.search.rejected.jvector", e.getMessage()));
      return;
    }
    long t2 = System.nanoTime();

    double inMemMillis = (t1 - t0) / 1.0e6;
    double jvectorMillis = (t2 - t1) / 1.0e6;

    StoreChoice active = activeStoreToggle.getValue();
    List<SearchHit> shown = (active == StoreChoice.JVECTOR) ? jvectorHits : inMemoryHits;
    shown = shown.stream()
        .sorted(Comparator.comparingDouble(SearchHit::score).reversed())
        .toList();
    hitsGrid.setItems(shown);

    timingLabel.setText(getTranslation("m02.timing.label",
        String.format(Locale.ROOT, "%.2f", inMemMillis),
        String.format(Locale.ROOT, "%.2f", jvectorMillis),
        getTranslation(active.labelKey())));
  }

  private void refreshSizeFooter() {
    int inMemSize = inMemoryStore != null ? inMemoryStore.size() : 0;
    int jvSize = jvectorStore != null ? jvectorStore.size() : 0;
    sizeFooter.setText(getTranslation("m02.size.footer", inMemSize, jvSize));
  }

  private String resolveModel() {
    String model = modelSelector.getValue();
    return (model == null || model.isBlank()) ? FALLBACK_EMBEDDING_MODEL : model;
  }

  // ---------- helpers -------------------------------------------------

  private static Optional<String> blankOr(String value) {
    return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value);
  }

  // ---------- value types ---------------------------------------------

  /** Which store backs the "shown" grid. Both stores are always live. */
  private enum StoreChoice {
    IN_MEMORY("m02.store.inmemory"),
    JVECTOR("m02.store.jvector");

    private final String labelKey;

    StoreChoice(String labelKey) {
      this.labelKey = labelKey;
    }

    String labelKey() {
      return labelKey;
    }
  }
}
