package com.svenruppert.flow.views.module01;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.WorkshopDefaults;
import com.svenruppert.flow.views.help.ExpandableHelp;
import com.svenruppert.flow.views.help.MarkdownSupport;
import com.svenruppert.flow.views.help.ParameterDocs;
import com.vaadin.flow.component.Component;
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
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.UploadHandler;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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
@CssImport("./styles/module01-view.css")
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

  private static final String FALLBACK_MODEL = WorkshopDefaults.DEFAULT_GENERATION_MODEL;

  private final LlmClient client;

  private final ComboBox<String> modelSelector = new ComboBox<>();
  private final Checkbox markdownToggle = new Checkbox();
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
    this(DefaultLlmClient.withDefaults());
  }

  /** Overload kept visible for tests or alternative wiring. */
  public Module01View(LlmClient client) {
    this.client = client;
    modelSelector.setLabel(getTranslation("m01.model.label"));
    markdownToggle.setLabel(getTranslation("m01.markdown.label"));
    markdownToggle.setValue(true);

    // Width only -- AppLayout scrolls the page natively; setSizeFull()
    // would pin the view to viewport height and clip overflow.
    setWidthFull();
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
    H3 title = new H3(getTranslation("m01.header.title"));
    Paragraph subtitle = new Paragraph(getTranslation("m01.header.subtitle"));
    subtitle.addClassName("m01-header-subtitle");
    VerticalLayout box = new VerticalLayout(title, subtitle);
    box.setPadding(false);
    box.setSpacing(false);
    return box;
  }

  private Component buildControlsRow() {
    // Model names can be long ("nomic-embed-text-v2-moe", "qwen2.5:7b-instruct-q4_K_M");
    // give the combo a generous fixed width so nothing is truncated.
    modelSelector.addClassName("m01-model-selector");

    // Each control gets a compact "What is this?" help panel directly
    // underneath via ExpandableHelp.pair(...); closed state is a single subtle
    // link-style line, open state expands the parameter's HTML body.
    HorizontalLayout row = new HorizontalLayout(
        ExpandableHelp.pair(modelSelector, ParameterDocs.M1_MODEL),
        ExpandableHelp.pair(markdownToggle, ParameterDocs.M1_MARKDOWN));
    row.setAlignItems(FlexComponent.Alignment.START);
    row.setSpacing(true);
    row.setWidthFull();
    return row;
  }

  private Component buildUploadRow() {
    upload.setAcceptedFileTypes("text/plain", ".txt", ".md", "text/markdown");
    upload.setMaxFiles(5);
    // Attachment bookkeeping is wired through the UploadHandler in the
    // field initialiser; only the reject listener lives here.
    upload.addFileRejectedListener(event ->
        Notification.show(getTranslation("m01.upload.rejected", event.getErrorMessage())));
    return upload;
  }

  private Component buildInputRow() {
    promptField.setPlaceholder(getTranslation("m01.prompt.placeholder"));
    promptField.setWidthFull();
    promptField.addClassName("m01-prompt-field");
    Button send = new Button(getTranslation("m01.send"), event -> onSend());
    send.addClassName("m01-send-button");
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
      Notification.show(getTranslation("m01.model.fallback", FALLBACK_MODEL));
    }
    modelSelector.setItems(names);
    modelSelector.setValue(WorkshopDefaults.preferredGenerationModel(names));
  }

  private void renderChips() {
    chips.removeAll();
    for (String name : attachments.keySet()) {
      Button chip = new Button(name + "  x");
      chip.getElement().setAttribute("title", getTranslation("m01.chip.remove.title"));
      chip.addClassName("m01-attachment-chip");
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
      Notification.show(getTranslation("m01.prompt.empty"));
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
      Notification.show(getTranslation("m01.no.reply"));
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
      MarkdownSupport.highlightCodeBlocks(conversation);
    }
  }

  /**
   * Runs highlight.js against every {@code <pre><code>} descendant of the
   * conversation layout. Retries briefly until {@code window.hljs} is
   * available, in case the CDN script has not finished loading yet.
   */
  private Component buildBubble(ChatMessage message) {
    Div bubble = new Div();
    bubble.addClassNames("m01-bubble",
        message.role() == Role.USER ? "m01-bubble--user" : "m01-bubble--assistant");

    String authorLabel = (message.role() == Role.USER
        ? getTranslation("m01.role.user")
        : getTranslation("m01.role.assistant")) + ": ";

    boolean renderAsMarkdown =
        message.role() == Role.ASSISTANT && Boolean.TRUE.equals(markdownToggle.getValue());

    if (renderAsMarkdown) {
      Span author = new Span(authorLabel);
      author.addClassName("m01-bubble-author");
      // Wrap in a single <div> so Vaadin's Html component sees a single root
      // element, as it requires.
      Component content = MarkdownSupport.htmlDiv(
          MarkdownSupport.renderSafeHtml(message.text()));
      content.getElement().getClassList().add("md");
      bubble.add(author, content);
    } else {
      // Plain-text path: preserve whitespace so participants can still read
      // raw Markdown source when they turn rendering off.
      bubble.setText(authorLabel + message.text());
      bubble.addClassName("m01-bubble--plain");
    }
    return bubble;
  }

  private Component buildLatencyLabel(Duration elapsed) {
    double seconds = elapsed.toMillis() / 1000.0;
    Span latency = new Span(String.format(Locale.ROOT, "%.2fs", seconds));
    latency.addClassName("m01-latency");
    return latency;
  }

  // ---------- value types ---------------------------------------------

  private enum Role { USER, ASSISTANT }

  private record ChatMessage(Role role, String text, Duration latency) implements Serializable {
  }
}
