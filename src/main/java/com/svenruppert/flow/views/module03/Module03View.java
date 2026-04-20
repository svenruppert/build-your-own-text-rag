package com.svenruppert.flow.views.module03;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.views.help.ExpandableHelp;
import com.svenruppert.flow.views.help.HelpEntry;
import com.svenruppert.flow.views.help.ParameterDocs;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.UploadHandler;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Chunking Lab -- module 3's user-facing half.
 *
 * <p>The participant feeds a piece of text into the text area, picks one
 * of four chunkers, and observes the output as coloured segments over
 * the source. Overlap zones look visually dense because the two chunks
 * share that range; non-overlapping zones wear a single, soft tint.
 *
 * <p>Nothing about rendering runs on the client: the Java code builds
 * the entire {@code <span>} structure, Vaadin's {@link Html} component
 * merely ships it to the DOM.
 */
@Route(value = Module03View.PATH, layout = MainLayout.class)
public class Module03View
    extends VerticalLayout
    implements HasLogger {

  public static final String PATH = "Module03";

  /** Soft palette; alpha is applied at render time. */
  private static final List<String> PALETTE = List.of(
      "#e57373", "#81c784", "#64b5f6", "#ffb74d",
      "#ba68c8", "#4dd0e1", "#aed581", "#ff8a65");

  private static final String DEFAULT_TEXT = """
      # Introduction

      Welcome to the Chunking Lab. Tweak the parameters below, pick a
      chunker, and watch how the same input becomes a different set of
      chunks. Overlap zones are visible as denser, coloured regions.

      ## Motivation

      Ingestion is where a surprising amount of retrieval quality is
      decided. Fixed-size chunking is fast but cuts words and sentences
      in half. Overlapping chunking keeps boundary-crossing information
      recoverable. Sentence chunking respects natural phrase limits.
      Structure-aware chunking aligns chunks with headings so each chunk
      carries its own breadcrumb path.

      ## Practical tip

      Try the same text with each chunker in turn. Pay attention to the
      overlap zones in the *Overlapping* tab, then flip to *Sentence*
      and see how sentence boundaries move the edges around.
      """;

  private static final Map<String, String> TAB_LABEL = Map.of(
      "FIXED", "Fixed size",
      "OVERLAP", "Overlapping",
      "SENTENCE", "Sentence",
      "STRUCTURE", "Structure-aware");

  // ------- input ------------------------------------------------------

  // The built-in label is suppressed; an explicit Span above the
  // text-area carries the "Document text" caption so the caption
  // alignment matches the right-hand panel exactly.
  private final TextArea textArea = new TextArea();
  /**
   * Vaadin 25 replaced {@code MemoryBuffer} + succeeded-listener with
   * {@link UploadHandler#inMemory}: the callback gets the full byte[]
   * of the completed upload, which we decode as UTF-8 and push into
   * the text area.
   */
  private final Upload upload = new Upload(
      UploadHandler.inMemory((metadata, bytes) -> {
        textArea.setValue(new String(bytes, StandardCharsets.UTF_8));
        Notification.show("Loaded " + metadata.fileName() + " into the text area.");
      }));

  // ------- tabs and per-tab parameters -------------------------------

  private final Tab tabFixed = new Tab(TAB_LABEL.get("FIXED"));
  private final Tab tabOverlap = new Tab(TAB_LABEL.get("OVERLAP"));
  private final Tab tabSentence = new Tab(TAB_LABEL.get("SENTENCE"));
  private final Tab tabStructure = new Tab(TAB_LABEL.get("STRUCTURE"));
  private final Tabs tabs = new Tabs(tabFixed, tabOverlap, tabSentence, tabStructure);

  private final IntegerField fixedSize = sizeField("Chunk size (chars)", 200);
  private final IntegerField overlapChunkSize = sizeField("Chunk size", 200);
  private final IntegerField overlapAmount = sizeField("Overlap", 40);
  private final IntegerField sentenceTarget = sizeField("Target size (chars)", 300);
  private final IntegerField structureTarget = sizeField("Target size", 400);

  private final VerticalLayout paramsBox = new VerticalLayout();

  // ------- output -----------------------------------------------------

  private final Html visualisation = new Html("<div class=\"chunk-viz\"></div>");
  private final VerticalLayout legend = new VerticalLayout();

  /** Last computed statistics line; shown on demand via the stats dialog. */
  private String lastStatsText = "Run 'Chunk it' to populate statistics.";

  // ------- state ------------------------------------------------------

  /** Highlights a specific chunk in the visualisation; -1 means "none". */
  private int focusedChunk = -1;

  /** Canonical text used in the visualisation after the last chunk run. */
  private String lastCanonicalText = "";

  /** Last chunk run, used to re-render on focus change. */
  private List<Chunk> lastChunks = List.of();

  // ------- view -------------------------------------------------------

  public Module03View() {
    setSizeFull();
    setPadding(true);
    setSpacing(true);

    add(buildStyleBlock());
    add(buildHeader());
    add(buildUploadRow());
    add(tabs);
    // Help for the chunker choice lives directly under the tab
    // strip, so the explanation sits where the eye already is.
    add(ExpandableHelp.of(ParameterDocs.M3_CHUNKER));
    add(buildParamsAndActionRow());
    add(legend);
    add(buildMainPanelsRow());

    textArea.setValue(DEFAULT_TEXT);

    tabs.addSelectedChangeListener(event -> renderParams());
    renderParams();
    updateStructureTabAvailability();
    textArea.addValueChangeListener(event -> updateStructureTabAvailability());
  }

  // ---------- fixed layout blocks ------------------------------------

  private Component buildStyleBlock() {
    // Inline CSS is kept visible on purpose -- participants should read
    // it as workshop material. Palette colours are exposed as CSS
    // variables so a trainer can tweak them without touching Java.
    StringBuilder css = new StringBuilder();
    css.append("<style>\n");
    css.append(":root {\n");
    for (int i = 0; i < PALETTE.size(); i++) {
      css.append("  --chunk-colour-").append(i).append(": ")
          .append(PALETTE.get(i)).append(";\n");
    }
    css.append("}\n");
    // Both panels share the same monospace font family, size and line
    // height so left and right read as one continuous typographic
    // surface, with matching wrap behaviour.
    css.append(".chunk-viz { font-family: ui-monospace, SFMono-Regular, monospace; "
        + "font-size: 0.9rem; line-height: 1.6; white-space: pre-wrap; "
        + "padding: 0.6em 0.75em; background: #fafafa; "
        + "border: 1px solid #ddd; border-radius: 4px; "
        + "width: 100%; height: 100%; box-sizing: border-box; "
        + "overflow: auto; }\n");
    // Wrapper around the Html component so it can participate in a
    // flex row and share the visual footprint of the input TextArea.
    css.append(".chunk-viz-wrapper { flex: 1; min-width: 0; display: flex; }\n");
    css.append(".chunk-viz-wrapper > * { width: 100%; }\n");
    // Restyle only the text-area that carries the `chunk-lab-doc`
    // scope class; the slotted <textarea> is outside the component's
    // shadow DOM and can be targeted from the document stylesheet.
    css.append("vaadin-text-area.chunk-lab-doc textarea { "
        + "font-family: ui-monospace, SFMono-Regular, monospace !important; "
        + "font-size: 0.9rem !important; line-height: 1.6 !important; }\n");
    // Identical caption styling for both panels so the controls below
    // start at the same vertical position.
    css.append(".panel-caption { font-size: 0.875rem; font-weight: 500; "
        + "color: #555; margin-bottom: 0.3em; display: block; }\n");
    css.append(".chunk-lab-panel { display: flex; flex-direction: column; "
        + "flex: 1; min-width: 0; }\n");
    css.append(".chunk-lab-panel > *:last-child { flex: 1; min-height: 0; }\n");
    // Horizontal chip strip: one chip per chunk, scrolls horizontally
    // so it stays compact even for a few hundred chunks.
    css.append(".chunk-chip-track { display: flex; flex-wrap: nowrap; "
        + "gap: 0.35em; overflow-x: auto; overflow-y: hidden; "
        + "padding: 0.25em 0; flex: 1; min-width: 0; }\n");
    css.append(".chunk-chip { flex-shrink: 0; min-width: 2.6em; "
        + "padding: 0 0.7em; font-family: ui-monospace, SFMono-Regular, monospace; "
        + "font-size: 0.8rem; border-radius: 999px; cursor: pointer; }\n");
    css.append(".chunk-viz span { padding: 1px 0; border-radius: 2px; }\n");
    css.append(".chunk-legend-entry { font-family: ui-monospace, SFMono-Regular, monospace; "
        + "font-size: 0.85rem; }\n");
    css.append(".chunk-swatch { display: inline-block; width: 0.9em; height: 0.9em; "
        + "margin-right: 0.5em; border: 1px solid #666; vertical-align: middle; }\n");
    css.append("</style>\n");
    return new Html(css.toString());
  }

  private Component buildHeader() {
    H3 title = new H3("Module 3 -- Chunking Lab");
    Paragraph subtitle = new Paragraph(
        "Four chunkers against the same text. Overlap zones show as visually "
            + "denser, coloured regions. Click an entry in the legend to highlight "
            + "that chunk in the text above.");
    subtitle.getStyle().set("color", "#666").set("margin-top", "-0.4em");
    VerticalLayout box = new VerticalLayout(title, subtitle);
    box.setPadding(false);
    box.setSpacing(false);
    return box;
  }

  /**
   * Compact upload row at the very top of the controls. The heavy UI is
   * below, so the upload is intentionally small.
   */
  private Component buildUploadRow() {
    upload.setAcceptedFileTypes("text/plain", "text/markdown", ".txt", ".md", ".markdown");
    upload.setMaxFiles(1);
    upload.setDropLabel(new Span("Drop a .txt or .md file here"));
    // Ingestion is wired through the UploadHandler in the field
    // initialiser -- no listener needed here.

    HorizontalLayout row = new HorizontalLayout(upload);
    row.setWidthFull();
    row.setAlignItems(FlexComponent.Alignment.END);
    return row;
  }

  /**
   * Per-tab parameter fields, the primary "Chunk it" action and a
   * "Stats..." button that opens the statistics dialog -- all on one
   * horizontal row right above the two panels they act on.
   */
  private Component buildParamsAndActionRow() {
    Button chunkIt = new Button("Chunk it", event -> onChunk());
    chunkIt.setMinWidth("8em");
    chunkIt.getStyle().set("flex-shrink", "0");

    Button statsButton = new Button("Show statistics", event -> openStatsDialog());
    statsButton.setMinWidth("10em");
    statsButton.getStyle().set("flex-shrink", "0");

    HorizontalLayout row = new HorizontalLayout(paramsBox, chunkIt, statsButton);
    row.setAlignItems(FlexComponent.Alignment.END);
    row.setSpacing(true);
    row.setWidthFull();
    row.setFlexGrow(1, paramsBox);
    return row;
  }

  /**
   * Side-by-side main panels. Both are wrapped in an identically
   * structured column (caption {@code <span>} on top, control below)
   * so the controls start at exactly the same vertical position.
   * Monospace font, font-size and line-height are shared so the two
   * panels read as one typographic surface.
   */
  private Component buildMainPanelsRow() {
    // Left column: caption + TextArea.
    textArea.addClassName("chunk-lab-doc");
    textArea.setSizeFull();

    Span leftCaption = new Span("Document text");
    leftCaption.addClassName("panel-caption");

    Div leftPanel = new Div(leftCaption, textArea);
    leftPanel.addClassName("chunk-lab-panel");

    // Right column: caption + viz wrapper containing the Html.
    Span rightCaption = new Span(
        "Chunk visualisation -- coloured per chunk; denser regions "
            + "indicate overlap");
    rightCaption.addClassName("panel-caption");

    Div vizWrapper = new Div(visualisation);
    vizWrapper.addClassName("chunk-viz-wrapper");

    Div rightPanel = new Div(rightCaption, vizWrapper);
    rightPanel.addClassName("chunk-lab-panel");

    HorizontalLayout row = new HorizontalLayout(leftPanel, rightPanel);
    row.setWidthFull();
    row.setHeight("34em");
    row.setAlignItems(FlexComponent.Alignment.STRETCH);
    row.setSpacing(true);
    return row;
  }

  /** Opens a small dialog with the chunk statistics of the latest run. */
  private void openStatsDialog() {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Chunk statistics");
    Paragraph body = new Paragraph(lastStatsText);
    body.getStyle().set("white-space", "pre-wrap").set("margin", "0");
    dialog.add(body);
    Button close = new Button("Close", e -> dialog.close());
    dialog.getFooter().add(close);
    dialog.open();
  }

  private static IntegerField sizeField(String label, int defaultValue) {
    IntegerField field = new IntegerField(label);
    field.setValue(defaultValue);
    field.setMin(1);
    field.setWidth("9em");
    return field;
  }

  // ---------- dynamic param row ---------------------------------------

  private void renderParams() {
    paramsBox.removeAll();
    paramsBox.setPadding(false);
    paramsBox.setSpacing(false);

    HorizontalLayout row = new HorizontalLayout();
    row.setAlignItems(FlexComponent.Alignment.START);
    row.setSpacing(true);

    Tab selected = tabs.getSelectedTab();
    if (selected == tabFixed) {
      row.add(withHelp(fixedSize, ParameterDocs.M3_CHUNK_SIZE));
    } else if (selected == tabOverlap) {
      row.add(
          withHelp(overlapChunkSize, ParameterDocs.M3_CHUNK_SIZE),
          withHelp(overlapAmount, ParameterDocs.M3_OVERLAP));
    } else if (selected == tabSentence) {
      row.add(withHelp(sentenceTarget, ParameterDocs.M3_CHUNK_SIZE));
    } else if (selected == tabStructure) {
      row.add(withHelp(structureTarget, ParameterDocs.M3_CHUNK_SIZE));
    }
    paramsBox.add(row);
  }

  /**
   * Pairs a per-tab size field with its inline help panel so the
   * explanation stays attached to its field when the tab switches.
   */
  private static VerticalLayout withHelp(Component control, HelpEntry entry) {
    VerticalLayout column = new VerticalLayout(control, ExpandableHelp.of(entry));
    column.setPadding(false);
    column.setSpacing(false);
    column.setWidth(null);
    return column;
  }

  private void updateStructureTabAvailability() {
    // "Structure-aware" is only meaningful when the input carries at
    // least one Markdown heading.
    boolean hasHeadings = extractHeadings(textArea.getValue()).hasHeadings();
    tabStructure.setEnabled(hasHeadings);
    if (!hasHeadings && tabs.getSelectedTab() == tabStructure) {
      tabs.setSelectedTab(tabFixed);
      renderParams();
    }
  }

  // ---------- action --------------------------------------------------

  private void onChunk() {
    String rawInput = textArea.getValue() == null ? "" : textArea.getValue();
    Extraction extraction = extractHeadings(rawInput);
    String canonical = extraction.canonicalText();

    if (canonical.isEmpty()) {
      Notification.show("Please enter some text first.");
      return;
    }

    Chunker chunker;
    try {
      Tab selected = tabs.getSelectedTab();
      if (selected == tabFixed) {
        chunker = new FixedSizeChunker(valueOr(fixedSize, 200));
      } else if (selected == tabOverlap) {
        int size = valueOr(overlapChunkSize, 200);
        int ov = valueOr(overlapAmount, 40);
        chunker = new OverlappingChunker(size, ov);
      } else if (selected == tabSentence) {
        chunker = new SentenceChunker(valueOr(sentenceTarget, 300));
      } else if (selected == tabStructure) {
        chunker = new StructureAwareChunker(
            valueOr(structureTarget, 400), extraction.headings());
      } else {
        throw new IllegalStateException("no tab selected");
      }
    } catch (IllegalArgumentException e) {
      Notification.show("Invalid parameter: " + e.getMessage());
      return;
    }

    List<Chunk> chunks = chunker.chunk(canonical);
    lastCanonicalText = canonical;
    lastChunks = chunks;
    focusedChunk = -1;

    updateStats(chunks, canonical);
    updateVisualisation();
    updateLegend();
  }

  private static int valueOr(IntegerField field, int fallback) {
    Integer v = field.getValue();
    return (v == null || v <= 0) ? fallback : v;
  }

  // ---------- stats (shown on demand via dialog) ---------------------

  /**
   * Recomputes the stats line after a chunk run and caches it in
   * {@link #lastStatsText} for {@link #openStatsDialog()} to show.
   */
  private void updateStats(List<Chunk> chunks, String canonical) {
    if (chunks.isEmpty()) {
      lastStatsText = "0 chunks generated.";
      return;
    }
    int totalChars = chunks.stream().mapToInt(c -> c.endOffset() - c.startOffset()).sum();
    double meanChars = totalChars / (double) chunks.size();

    // Overlap regions = positions covered by more than one chunk.
    int[] coverage = new int[canonical.length()];
    for (Chunk c : chunks) {
      for (int i = c.startOffset(); i < c.endOffset(); i++) coverage[i]++;
    }
    int overlapChars = 0;
    for (int c : coverage) if (c > 1) overlapChars++;

    lastStatsText = String.format(Locale.ROOT,
        "Chunks: %d%nTotal coverage: %,d chars%nMean chunk size: %.1f chars%nOverlap regions: %,d chars",
        chunks.size(), totalChars, meanChars, overlapChars);
  }

  // ---------- visualisation ------------------------------------------

  /**
   * Builds the {@code <span>} chain for the current canonical text and
   * the current chunks. Each position's set of owning chunks determines
   * its styling: single-chunk positions get a soft tint; overlap zones
   * get a stronger tint plus a bottom border in the second chunk's
   * colour. If a chunk is focused, its segments are promoted to a
   * heavier tint.
   */
  private void updateVisualisation() {
    if (lastCanonicalText.isEmpty() || lastChunks.isEmpty()) {
      visualisation.setHtmlContent("<div class=\"chunk-viz\"></div>");
      return;
    }
    List<List<Integer>> owners = computeOwners(lastCanonicalText, lastChunks);

    StringBuilder html = new StringBuilder();
    html.append("<div class=\"chunk-viz\">");

    int segmentStart = 0;
    List<Integer> currentOwners = owners.get(0);
    for (int i = 1; i <= lastCanonicalText.length(); i++) {
      List<Integer> next = (i == lastCanonicalText.length()) ? null : owners.get(i);
      if (next == null || !next.equals(currentOwners)) {
        appendSegment(html,
            lastCanonicalText.substring(segmentStart, i),
            currentOwners);
        segmentStart = i;
        currentOwners = next;
      }
    }
    html.append("</div>");
    visualisation.setHtmlContent(html.toString());
  }

  private List<List<Integer>> computeOwners(String text, List<Chunk> chunks) {
    List<List<Integer>> owners = new ArrayList<>(text.length());
    for (int i = 0; i < text.length(); i++) owners.add(new ArrayList<>(2));
    for (Chunk c : chunks) {
      for (int i = c.startOffset(); i < c.endOffset() && i < text.length(); i++) {
        owners.get(i).add(c.index());
      }
    }
    return owners;
  }

  private void appendSegment(StringBuilder html, String segment, List<Integer> owners) {
    String style = styleFor(owners);
    html.append("<span style=\"").append(style).append("\">");
    html.append(escapeHtml(segment));
    html.append("</span>");
  }

  private String styleFor(List<Integer> owners) {
    if (owners.isEmpty()) return "";
    if (owners.size() == 1) {
      int idx = owners.get(0);
      double alpha = (idx == focusedChunk) ? 0.40 : 0.15;
      return "background-color:" + rgba(colourFor(idx), alpha) + ";";
    }
    // Two (or more) chunks overlap at this position: colour the background
    // with the first chunk, add a bottom border in the second chunk's
    // colour. That makes overlap visible as "denser + two-toned" while
    // keeping both chunks' identities.
    int top = owners.get(0);
    int bottom = owners.get(1);
    double topAlpha = (focusedChunk == top || focusedChunk == bottom) ? 0.55 : 0.35;
    String bg = rgba(colourFor(top), topAlpha);
    String border = rgba(colourFor(bottom), 0.85);
    return "background-color:" + bg
        + "; border-bottom: 2px solid " + border + ";";
  }

  // ---------- legend -------------------------------------------------

  /**
   * Horizontally scrollable chip strip placed right above the two
   * panels. Each chip carries the chunk number in its palette colour;
   * the full descriptor (offsets, length, optional heading path) is
   * shown as the chip's tooltip. Clicking a chip toggles focus, which
   * re-renders the visualisation with the chosen chunk highlighted.
   *
   * <p>The strip scales to a hundred-plus chunks without pushing the
   * main panels off the screen -- overflow is horizontal.
   */
  private void updateLegend() {
    legend.removeAll();
    legend.setPadding(false);
    legend.setSpacing(false);
    legend.setWidthFull();

    if (lastChunks.isEmpty()) return;

    Span heading = new Span("Chunks (" + lastChunks.size() + ")");
    heading.addClassName("panel-caption");
    heading.getStyle().set("margin-bottom", "0").set("margin-right", "0.75em");

    Div chipTrack = new Div();
    chipTrack.addClassName("chunk-chip-track");
    for (Chunk c : lastChunks) {
      chipTrack.add(buildChip(c));
    }

    HorizontalLayout row = new HorizontalLayout(heading, chipTrack);
    row.setWidthFull();
    row.setAlignItems(FlexComponent.Alignment.CENTER);
    row.setSpacing(false);
    row.setFlexGrow(1, chipTrack);
    row.getStyle().set("min-width", "0");
    legend.add(row);
  }

  private Component buildChip(Chunk c) {
    boolean focused = (focusedChunk == c.index());
    Button chip = new Button(String.valueOf(c.index()));
    chip.addClassName("chunk-chip");
    chip.getStyle()
        .set("background-color", rgba(colourFor(c.index()), focused ? 0.75 : 0.35))
        .set("color", "#222")
        .set("border", focused ? "1.5px solid #333" : "1px solid #ccc")
        .set("font-weight", focused ? "600" : "500");

    StringBuilder tip = new StringBuilder();
    tip.append("Chunk ").append(c.index())
        .append("  .  offsets ").append(c.startOffset())
        .append('-').append(c.endOffset())
        .append("  .  ").append(c.endOffset() - c.startOffset()).append(" chars");
    if (c.metadata().containsKey(Chunk.HEADING_PATH)) {
      tip.append("\n").append(c.metadata().get(Chunk.HEADING_PATH));
    }
    chip.setTooltipText(tip.toString());

    int targetIndex = c.index();
    chip.addClickListener(click -> {
      focusedChunk = (focusedChunk == targetIndex) ? -1 : targetIndex;
      updateVisualisation();
      updateLegend();
    });
    return chip;
  }

  // ---------- utilities ----------------------------------------------

  /**
   * Decides what text the chunkers should operate on. If the input
   * contains at least one Markdown heading, the canonical text is the
   * extractor's plain-text output, paired with the heading list. Pure
   * plain-text input is passed through verbatim to avoid surprising
   * whitespace normalisation.
   */
  private Extraction extractHeadings(String input) {
    if (input == null || input.isEmpty()) {
      return new Extraction("", List.of());
    }
    MarkdownTextExtractor.ExtractionResult result =
        new MarkdownTextExtractor().extract(input);
    if (result.headings().isEmpty()) {
      return new Extraction(input, List.of());
    }
    return new Extraction(result.plainText(), result.headings());
  }

  private String colourFor(int chunkIndex) {
    return PALETTE.get(Math.floorMod(chunkIndex, PALETTE.size()));
  }

  private static String rgba(String hex, double alpha) {
    int r = Integer.parseInt(hex.substring(1, 3), 16);
    int g = Integer.parseInt(hex.substring(3, 5), 16);
    int b = Integer.parseInt(hex.substring(5, 7), 16);
    return String.format(Locale.ROOT, "rgba(%d,%d,%d,%.2f)", r, g, b, alpha);
  }

  private static String escapeHtml(String s) {
    StringBuilder out = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '&' -> out.append("&amp;");
        case '<' -> out.append("&lt;");
        case '>' -> out.append("&gt;");
        case '"' -> out.append("&quot;");
        case '\'' -> out.append("&#39;");
        default -> out.append(c);
      }
    }
    return out.toString();
  }

  // ---------- value types --------------------------------------------

  private record Extraction(String canonicalText, List<HeadingInfo> headings) {
    boolean hasHeadings() {
      return !headings.isEmpty();
    }
  }
}
