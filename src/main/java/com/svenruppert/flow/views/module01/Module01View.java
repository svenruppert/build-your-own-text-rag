package com.svenruppert.flow.views.module01;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.views.help.ExpandableHelp;
import com.svenruppert.flow.views.help.HelpEntry;
import com.svenruppert.flow.views.help.ParameterDocs;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.UploadHandler;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Two-stage chat UI against a local Ollama server -- the user-facing half
 * of module 1.
 *
 * <p>Stage 1 -- type a prompt and send. The model answers from its
 * pre-training only. Participants are encouraged to ask about their own
 * domain data and observe the model hallucinate or decline.
 *
 * <p>Stage 2 -- attach one or more {@code .txt}/{@code .md} files.
 * When documents are attached, the UI calls
 * {@link LlmClient#generate(String, String, java.util.List)} which
 * concatenates them into the prompt. Answers become grounded -- and
 * the prompt grows fast, which is the motivation for every later module.
 *
 * <p>LLMs frequently produce Markdown (lists, headings, code fences). A
 * toggle above the chat history renders assistant bubbles as Markdown
 * when on, and as plain text when off. Flipping the toggle re-renders
 * the whole history in place.
 */
@Route(value = Module01View.PATH, layout = MainLayout.class)
// highlight.js colourises fenced code blocks in assistant replies
// (commonmark emits <pre><code class="language-xxx">). The library,
// its GitHub theme and a small ES-module loader are bundled under
//   src/main/frontend/highlight/
// so the workshop runs fully offline. The loader is a Vite-processed
// ES module; highlight.min.js is a classic browser script whose
// top-level `var hljs` only attaches to `window` when it runs in
// global scope -- hence the dynamic <script>-injection trick inside
// hljs-loader.js.
@JsModule("./highlight/hljs-loader.js")
public class Module01View
    extends VerticalLayout
    implements HasLogger {

  public static final String PATH = "Module01";

  private static final String FALLBACK_MODEL = "llama3.2";
  private static final String USER_BUBBLE_COLOUR = "#e7f0ff";
  private static final String ASSISTANT_BUBBLE_COLOUR = "#f3f3f3";

  // One shared Markdown pipeline. Parser and renderer are stateless and
  // safe to reuse across the entire session.
  // GFM pipe tables are not in the CommonMark core; LLMs love them, so we
  // pull in the commonmark-ext-gfm-tables extension on both ends of the
  // pipeline.
  private static final Set<org.commonmark.Extension> MARKDOWN_EXTENSIONS =
      Set.of(TablesExtension.create());
  private static final Parser MARKDOWN_PARSER = Parser.builder()
      .extensions(MARKDOWN_EXTENSIONS)
      .build();
  private static final HtmlRenderer MARKDOWN_RENDERER = HtmlRenderer.builder()
      .extensions(MARKDOWN_EXTENSIONS)
      // Escape raw HTML embedded in the Markdown source to avoid letting
      // a rogue LLM output <script> tags into the UI.
      .escapeHtml(true)
      .sanitizeUrls(true)
      .build();

  private final LlmClient client;

  private final ComboBox<String> modelSelector = new ComboBox<>("Model");
  private final Checkbox markdownToggle = new Checkbox("Render Markdown", true);
  private final TextArea promptField = new TextArea();
  private final VerticalLayout conversation = new VerticalLayout();
  private final HorizontalLayout chips = new HorizontalLayout();

  /** Preserves upload order; keys are filenames, values are UTF-8 contents. */
  private final Map<String, String> attachments = new LinkedHashMap<>();

  /**
   * Vaadin 25 replaced {@code MultiFileMemoryBuffer} + succeeded-listener
   * with {@link UploadHandler#inMemory}: the callback receives the
   * completed bytes directly. Runs with the session lock held, so the
   * chip-strip update is safe to do inline.
   */
  private final Upload upload = new Upload(
      UploadHandler.inMemory((metadata, bytes) -> {
        attachments.put(metadata.fileName(),
            new String(bytes, StandardCharsets.UTF_8));
        renderChips();
      }));

  /** Backing store for rendered bubbles, so we can re-render on toggle change. */
  private final List<ChatMessage> history = new ArrayList<>();

  /** Default constructor used by Vaadin's route registry. */
  public Module01View() {
    this(new DefaultLlmClient(LlmConfig.defaults()));
  }

  /** Overload kept visible for tests or alternative wiring. */
  public Module01View(LlmClient client) {
    this.client = client;
    setSizeFull();
    setPadding(true);
    setSpacing(true);

    add(buildHeader());
    add(buildControlsRow());
    add(conversation);
    add(buildUploadRow());
    add(chips);
    add(buildInputRow());

    conversation.setWidthFull();
    conversation.setPadding(false);
    conversation.setSpacing(false);
    chips.setWidthFull();

    markdownToggle.addValueChangeListener(event -> renderConversation());

    populateModels();
  }

  // ---------- layout --------------------------------------------------

  private Component buildHeader() {
    H3 title = new H3("Module 1 -- Talking to Ollama, naively");
    Paragraph subtitle = new Paragraph(
        "Stage 1: ask anything. Stage 2: attach documents and ask again.");
    subtitle.getStyle().set("color", "#666").set("margin-top", "-0.4em");
    VerticalLayout box = new VerticalLayout(title, subtitle);
    box.setPadding(false);
    box.setSpacing(false);
    return box;
  }

  private Component buildControlsRow() {
    // Model names can be long ("nomic-embed-text", "qwen2.5:7b-instruct-q4_K_M");
    // give the combo a generous fixed width so nothing is truncated.
    modelSelector.setWidth("24em");
    modelSelector.setMinWidth("20em");

    // Each control gets a compact "What is this?" help panel directly
    // underneath via withHelp(...); closed state is a single subtle
    // link-style line, open state expands the parameter's HTML body.
    HorizontalLayout row = new HorizontalLayout(
        withHelp(modelSelector, ParameterDocs.M1_MODEL),
        withHelp(markdownToggle, ParameterDocs.M1_MARKDOWN));
    row.setAlignItems(FlexComponent.Alignment.START);
    row.setSpacing(true);
    row.setWidthFull();
    return row;
  }

  /**
   * Pairs a user-facing control with its inline help panel, stacked
   * vertically in a tight column. The column preserves the control's
   * natural width and lets the help's "What is this?" link sit as a
   * subtle footnote directly underneath.
   */
  private static VerticalLayout withHelp(Component control, HelpEntry entry) {
    VerticalLayout column = new VerticalLayout(control, ExpandableHelp.of(entry));
    column.setPadding(false);
    column.setSpacing(false);
    column.setWidth(null);
    return column;
  }

  private Component buildUploadRow() {
    upload.setAcceptedFileTypes("text/plain", ".txt", ".md", "text/markdown");
    upload.setMaxFiles(5);
    // Attachment bookkeeping is wired through the UploadHandler in the
    // field initialiser; only the reject listener lives here.
    upload.addFileRejectedListener(event ->
        Notification.show("Rejected: " + event.getErrorMessage()));
    return upload;
  }

  private Component buildInputRow() {
    promptField.setPlaceholder("Ask a question...");
    promptField.setWidthFull();
    promptField.setMinHeight("6em");
    Button send = new Button("Send", event -> onSend());
    send.getStyle().set("align-self", "flex-end");
    HorizontalLayout row = new HorizontalLayout(promptField, send);
    row.setWidthFull();
    row.setAlignItems(FlexComponent.Alignment.END);
    row.setFlexGrow(1, promptField);
    return row;
  }

  // ---------- behaviour -----------------------------------------------

  private void populateModels() {
    List<String> names = client.listModels().orElse(List.of());
    if (names.isEmpty()) {
      // Either Ollama is not running or no models are pulled.
      // Fall back to a sane default so the UI remains usable.
      names = List.of(FALLBACK_MODEL);
      Notification.show(
          "Could not fetch model list from Ollama -- using fallback '"
              + FALLBACK_MODEL + "'.");
    }
    modelSelector.setItems(names);
    modelSelector.setValue(names.get(0));
  }

  private void renderChips() {
    chips.removeAll();
    for (String name : attachments.keySet()) {
      Button chip = new Button(name + "  x");
      chip.getElement().setAttribute("title", "Click to remove");
      chip.getStyle()
          .set("background", "#eef")
          .set("color", "#224")
          .set("border-radius", "999px")
          .set("padding", "0.2em 0.9em")
          .set("font-size", "0.85rem")
          .set("cursor", "pointer");
      chip.addClickListener(event -> {
        attachments.remove(name);
        renderChips();
      });
      chips.add(chip);
    }
  }

  private void onSend() {
    String prompt = promptField.getValue();
    if (prompt == null || prompt.isBlank()) {
      Notification.show("Type a prompt first.");
      return;
    }
    String model = modelSelector.getValue();
    if (model == null || model.isBlank()) model = FALLBACK_MODEL;

    history.add(new ChatMessage(Role.USER, prompt, null));
    renderConversation();
    promptField.clear();

    long start = System.nanoTime();
    Optional<String> reply = attachments.isEmpty()
        ? client.generate(prompt, model)
        : client.generate(prompt, model, List.copyOf(attachments.values()));
    Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

    if (reply.isPresent()) {
      history.add(new ChatMessage(Role.ASSISTANT, reply.get(), elapsed));
      renderConversation();
    } else {
      logger().warn("No reply from Ollama for model '{}'", model);
      Notification.show("No reply from Ollama -- check the server log.");
    }
  }

  private void renderConversation() {
    conversation.removeAll();
    for (ChatMessage message : history) {
      conversation.add(buildBubble(message));
      if (message.role() == Role.ASSISTANT && message.latency() != null) {
        conversation.add(buildLatencyLabel(message.latency()));
      }
    }
    // Colourise any fenced code blocks produced by the Markdown renderer.
    // No-op in plain-text mode because there are no <pre><code> elements.
    if (Boolean.TRUE.equals(markdownToggle.getValue())) {
      highlightCodeBlocks();
    }
  }

  /**
   * Runs highlight.js against every {@code <pre><code>} descendant of the
   * conversation layout. Retries briefly until {@code window.hljs} is
   * available, in case the CDN script has not finished loading yet.
   */
  private void highlightCodeBlocks() {
    conversation.getElement().executeJs(
        "const host = this;"
            + "function run() {"
            + "  if (window.hljs) {"
            + "    host.querySelectorAll('pre code').forEach("
            + "      c => window.hljs.highlightElement(c));"
            + "  } else {"
            + "    setTimeout(run, 50);"
            + "  }"
            + "}"
            + "run();");
  }

  private Component buildBubble(ChatMessage message) {
    Div bubble = new Div();
    bubble.getStyle()
        .set("background",
            message.role() == Role.USER ? USER_BUBBLE_COLOUR : ASSISTANT_BUBBLE_COLOUR)
        .set("padding", "0.6em 0.9em")
        .set("border-radius", "0.6em")
        .set("margin-bottom", "0.3em")
        .set("max-width", "100%");

    String authorLabel = (message.role() == Role.USER ? "You" : "Assistant") + ": ";

    boolean renderAsMarkdown =
        message.role() == Role.ASSISTANT && Boolean.TRUE.equals(markdownToggle.getValue());

    if (renderAsMarkdown) {
      Span author = new Span(authorLabel);
      author.getStyle().set("font-weight", "600");
      // Wrap in a single <div> so Vaadin's Html component sees a single root
      // element, as it requires.
      String html = "<div class=\"md\">"
          + MARKDOWN_RENDERER.render(MARKDOWN_PARSER.parse(message.text()))
          + "</div>";
      Html content = new Html(html);
      bubble.add(author, content);
    } else {
      // Plain-text path: preserve whitespace so participants can still read
      // raw Markdown source when they turn rendering off.
      bubble.setText(authorLabel + message.text());
      bubble.getStyle().set("white-space", "pre-wrap");
    }
    return bubble;
  }

  private Component buildLatencyLabel(Duration elapsed) {
    double seconds = elapsed.toMillis() / 1000.0;
    Span latency = new Span(String.format(Locale.ROOT, "%.2fs", seconds));
    latency.getStyle()
        .set("font-size", "0.75rem")
        .set("color", "#666")
        .set("margin-bottom", "0.7em")
        .set("align-self", "flex-end");
    return latency;
  }

  // ---------- value types ---------------------------------------------

  private enum Role { USER, ASSISTANT }

  private record ChatMessage(Role role, String text, Duration latency) implements Serializable {
  }
}
