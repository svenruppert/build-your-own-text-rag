package com.svenruppert.flow.views.module02;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.views.module01.DefaultLlmClient;
import com.svenruppert.flow.views.module01.LlmClient;
import com.svenruppert.flow.views.module01.LlmConfig;
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

  private static final String FALLBACK_EMBEDDING_MODEL = "nomic-embed-text";

  private final LlmClient llmClient;

  private final ComboBox<String> modelSelector = new ComboBox<>("Embedding model");
  private final RadioButtonGroup<StoreChoice> activeStoreToggle = new RadioButtonGroup<>();
  private final TextField addTextField = new TextField("Text to embed");
  private final TextField addIdField = new TextField("Id (optional -- auto if empty)");
  private final TextField addPayloadField = new TextField("Payload (optional)");
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

    setSizeFull();
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

    HorizontalLayout row = new HorizontalLayout(modelSelector, activeStoreToggle);
    row.setAlignItems(FlexComponent.Alignment.END);
    row.setSpacing(true);
    row.setWidthFull();
    return row;
  }

  private Component buildAddRow() {
    addTextField.setWidth("22em");
    addIdField.setWidth("14em");
    addPayloadField.setWidth("14em");

    Button addButton = new Button("Add to both stores", event -> onAdd());

    HorizontalLayout row = new HorizontalLayout(
        addTextField, addIdField, addPayloadField, addButton);
    row.setAlignItems(FlexComponent.Alignment.END);
    row.setSpacing(true);
    row.setWidthFull();
    return row;
  }

  private Component buildQueryRow() {
    queryField.setWidth("28em");
    topKField.setWidth("6em");

    Button searchButton = new Button("Search", event -> onSearch());

    HorizontalLayout row = new HorizontalLayout(queryField, topKField, searchButton);
    row.setAlignItems(FlexComponent.Alignment.END);
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
    String preferred = names.stream()
        .filter(n -> n.contains("embed"))
        .findFirst()
        .orElse(names.get(0));
    modelSelector.setValue(preferred);
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
