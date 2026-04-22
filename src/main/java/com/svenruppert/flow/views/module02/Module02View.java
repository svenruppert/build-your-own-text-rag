package com.svenruppert.flow.views.module02;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.WorkshopDefaults;
import com.svenruppert.flow.views.help.ExpandableHelp;
import com.svenruppert.flow.views.help.HelpEntry;
import com.svenruppert.flow.views.help.ParameterDocs;
import com.svenruppert.flow.views.module01.DefaultLlmClient;
import com.svenruppert.flow.views.module01.LlmClient;
import com.svenruppert.flow.views.module01.LlmConfig;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
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
import java.util.stream.Stream;

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
public class Module02View
    extends VerticalLayout
    implements HasLogger {

  public static final String PATH = "Module02";

  private static final String FALLBACK_EMBEDDING_MODEL =
      WorkshopDefaults.DEFAULT_EMBEDDING_MODEL;

  private final LlmClient llmClient;

  private final ComboBox<String> modelSelector = new ComboBox<>("Embedding model");
  private final RadioButtonGroup<StoreChoice> activeStoreToggle = new RadioButtonGroup<>();
  private final TextField addTextField = new TextField("Text to embed");
  private final TextField addIdField = new TextField("Id (optional -- auto if empty)");
  private final TextField addPayloadField = new TextField("Payload (optional)");
  private final TextArea bulkField = new TextArea("Bulk add -- one text per line");
  private final TextField queryField = new TextField("Query text");
  private final IntegerField topKField = new IntegerField("Top k");
  private final Grid<SearchHit> hitsGrid = new Grid<>(SearchHit.class, false);
  private final Span timingLabel = new Span();
  private final Span sizeFooter = new Span();

  private InMemoryVectorStore inMemoryStore;
  private EclipseStoreJVectorStore jvectorStore;
  private Path jvectorStorageDir;

  public Module02View() {
    this(new DefaultLlmClient(LlmConfig.defaults()));
  }

  public Module02View(LlmClient llmClient) {
    this.llmClient = llmClient;

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
    activeStoreToggle.setItemLabelGenerator(StoreChoice::label);
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
      Notification.show("Could not create temporary storage: " + e.getMessage());
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
      deleteRecursively(jvectorStorageDir);
    }
    super.onDetach(detachEvent);
  }

  // ---------- layout --------------------------------------------------

  private Component buildHeader() {
    H3 title = new H3("Module 2 -- Vector Store Lab");
    Paragraph subtitle = new Paragraph(
        "Swap the implementation at runtime -- the contract is identical. "
            + "Both stores are populated in lock-step so the toggle always "
            + "points at the same data. Timings are measured on both.");
    subtitle.getStyle().set("color", "#666").set("margin-top", "-0.4em");
    VerticalLayout box = new VerticalLayout(title, subtitle);
    box.setPadding(false);
    box.setSpacing(false);
    return box;
  }

  private Component buildControlsRow() {
    modelSelector.setWidth("22em");
    modelSelector.setMinWidth("18em");
    activeStoreToggle.getStyle().set("margin-left", "1em");

    HorizontalLayout row = new HorizontalLayout(
        withHelp(modelSelector, ParameterDocs.M2_EMBEDDING_MODEL),
        withHelp(activeStoreToggle, ParameterDocs.M2_ACTIVE_STORE));
    row.setAlignItems(FlexComponent.Alignment.START);
    row.setSpacing(true);
    row.setWidthFull();
    return row;
  }

  /**
   * Pairs a user-facing control with its inline help panel in a
   * tight vertical column. Mirrors the pattern used across the
   * workshop's module views.
   */
  private static VerticalLayout withHelp(Component control, HelpEntry entry) {
    VerticalLayout column = new VerticalLayout(control, ExpandableHelp.of(entry));
    column.setPadding(false);
    column.setSpacing(false);
    column.setWidth(null);
    return column;
  }

  private Component buildAddRow() {
    addTextField.setWidth("22em");
    addIdField.setWidth("14em");
    addPayloadField.setWidth("14em");

    Button addButton = new Button("Add to both stores", event -> onAdd());

    // ID and payload get inline help because participants routinely
    // guess at them; the text field is self-explanatory and stays bare.
    HorizontalLayout singleRow = new HorizontalLayout(
        addTextField,
        withHelp(addIdField, ParameterDocs.M2_ADD_ID),
        withHelp(addPayloadField, ParameterDocs.M2_ADD_PAYLOAD),
        addButton);
    singleRow.setAlignItems(FlexComponent.Alignment.START);
    singleRow.setSpacing(true);
    singleRow.setWidthFull();

    // Bulk input lets participants paste or type a batch of texts in
    // one go; each non-blank line becomes its own entry with an
    // auto-generated id and the line text as payload. Much faster
    // than round-tripping the single-row form for every example.
    bulkField.setWidthFull();
    bulkField.setMinHeight("10em");
    bulkField.setPlaceholder(
        "Paste or type multiple texts, one per line -- each line becomes its own entry."
            + "\n\nExample:"
            + "\nEclipseStore persists Java object graphs without an ORM."
            + "\nJVector is an HNSW index library written in Java."
            + "\nOllama serves local models through a small HTTP API.");
    bulkField.setHelperText(
        "Empty lines are skipped. Id is auto-generated; payload equals the line text.");

    Button bulkAddButton = new Button("Add all lines", event -> onBulkAdd());
    Button clearBulkButton = new Button("Clear", event -> bulkField.clear());

    HorizontalLayout bulkButtonRow = new HorizontalLayout(bulkAddButton, clearBulkButton);
    bulkButtonRow.setSpacing(true);
    bulkButtonRow.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
    bulkButtonRow.setWidthFull();

    // Visible section divider + heading so the bulk area is obviously
    // a second, separate input path rather than an optional footer
    // hidden off-screen.
    Span sectionTitle = new Span("Bulk add -- one entry per line");
    sectionTitle.getStyle()
        .set("font-weight", "600")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("font-size", "0.95rem");
    Div divider = new Div();
    divider.getStyle()
        .set("border-top", "1px solid var(--lumo-contrast-10pct)")
        .set("margin", "var(--lumo-space-m) 0 var(--lumo-space-xs) 0")
        .set("width", "100%");

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
    queryField.setWidth("28em");
    topKField.setWidth("6em");

    Button searchButton = new Button("Search", event -> onSearch());

    HorizontalLayout row = new HorizontalLayout(
        queryField,
        withHelp(topKField, ParameterDocs.M2_TOP_K),
        searchButton);
    row.setAlignItems(FlexComponent.Alignment.START);
    row.setSpacing(true);
    row.setWidthFull();
    return row;
  }

  private Component buildResultsSection() {
    hitsGrid.addColumn(SearchHit::id).setHeader("Id").setAutoWidth(true);
    hitsGrid.addColumn(h -> String.format(Locale.ROOT, "%.4f", h.score()))
        .setHeader("Score").setAutoWidth(true);
    hitsGrid.addColumn(SearchHit::payload).setHeader("Payload").setFlexGrow(1);
    hitsGrid.setAllRowsVisible(true);
    hitsGrid.setWidthFull();

    timingLabel.getStyle()
        .set("color", "#555")
        .set("font-size", "0.85rem")
        .set("font-family", "monospace");

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
      Notification.show(
          "Could not fetch model list from Ollama -- using fallback '"
              + FALLBACK_EMBEDDING_MODEL + "'.");
    }
    modelSelector.setItems(names);
    modelSelector.setValue(WorkshopDefaults.preferredEmbeddingModel(names));
  }

  private void onAdd() {
    if (inMemoryStore == null || jvectorStore == null) {
      Notification.show("Stores are not initialised yet.");
      return;
    }
    String text = addTextField.getValue();
    if (text == null || text.isBlank()) {
      Notification.show("Enter some text to embed first.");
      return;
    }
    String model = resolveModel();
    Optional<float[]> maybeVec = llmClient.embed(text, model);
    if (maybeVec.isEmpty()) {
      Notification.show("Embedding failed -- see the server log.");
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
      Notification.show("Rejected: " + e.getMessage()
          + " (did you change the embedding model?)");
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
      Notification.show("Stores are not initialised yet.");
      return;
    }
    String raw = bulkField.getValue();
    if (raw == null || raw.isBlank()) {
      Notification.show("Enter at least one line to embed first.");
      return;
    }
    List<String> lines = raw.lines()
        .map(String::strip)
        .filter(s -> !s.isEmpty())
        .toList();
    if (lines.isEmpty()) {
      Notification.show("All lines were blank.");
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

    StringBuilder msg = new StringBuilder("Bulk add: ")
        .append(added).append(" added");
    if (embedFailed > 0) msg.append(", ").append(embedFailed).append(" embed-failed");
    if (rejected > 0) msg.append(", ").append(rejected)
        .append(" rejected (dimension clash -- stopped early)");
    Notification.show(msg.toString());

    if (added > 0 && embedFailed == 0 && rejected == 0) {
      bulkField.clear();
    }
    refreshSizeFooter();
  }

  private void onSearch() {
    if (inMemoryStore == null || jvectorStore == null) {
      Notification.show("Stores are not initialised yet.");
      return;
    }
    String text = queryField.getValue();
    if (text == null || text.isBlank()) {
      Notification.show("Enter a query first.");
      return;
    }
    Integer k = topKField.getValue();
    if (k == null || k <= 0) k = 5;

    String model = resolveModel();
    Optional<float[]> maybeVec = llmClient.embed(text, model);
    if (maybeVec.isEmpty()) {
      Notification.show("Query embedding failed -- see the server log.");
      return;
    }
    float[] query = maybeVec.get();

    // Search both stores, time both.
    long t0 = System.nanoTime();
    List<SearchHit> inMemoryHits;
    try {
      inMemoryHits = inMemoryStore.search(query, k);
    } catch (IllegalArgumentException e) {
      Notification.show("InMemory search rejected: " + e.getMessage());
      return;
    }
    long t1 = System.nanoTime();
    List<SearchHit> jvectorHits;
    try {
      jvectorHits = jvectorStore.search(query, k);
    } catch (IllegalArgumentException e) {
      Notification.show("JVector search rejected: " + e.getMessage());
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

    timingLabel.setText(String.format(Locale.ROOT,
        "InMemory: %.2f ms  .  JVector: %.2f ms  .  showing %s",
        inMemMillis, jvectorMillis, active.label()));
  }

  private void refreshSizeFooter() {
    int inMemSize = inMemoryStore != null ? inMemoryStore.size() : 0;
    int jvSize = jvectorStore != null ? jvectorStore.size() : 0;
    sizeFooter.setText(String.format(Locale.ROOT,
        "Size: InMemory=%d  .  JVector=%d", inMemSize, jvSize));
    sizeFooter.getStyle()
        .set("color", "#555")
        .set("font-size", "0.85rem")
        .set("font-family", "monospace");
  }

  private String resolveModel() {
    String model = modelSelector.getValue();
    return (model == null || model.isBlank()) ? FALLBACK_EMBEDDING_MODEL : model;
  }

  // ---------- helpers -------------------------------------------------

  private static Optional<String> blankOr(String value) {
    return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value);
  }

  private void deleteRecursively(Path root) {
    if (root == null) return;
    try (Stream<Path> walk = Files.walk(root)) {
      walk.sorted(Comparator.reverseOrder()).forEach(p -> {
        try {
          Files.deleteIfExists(p);
        } catch (IOException ignored) {
          // robust: leave it to the OS cleanup rather than fail detach.
        }
      });
    } catch (IOException e) {
      logger().warn("Could not delete {}: {}", root, e.getMessage());
    }
  }

  // ---------- value types ---------------------------------------------

  /** Which store backs the "shown" grid. Both stores are always live. */
  private enum StoreChoice {
    IN_MEMORY("InMemoryVectorStore"),
    JVECTOR("EclipseStoreJVectorStore");

    private final String label;

    StoreChoice(String label) {
      this.label = label;
    }

    String label() {
      return label;
    }
  }
}
