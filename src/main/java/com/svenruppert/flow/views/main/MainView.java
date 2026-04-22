package com.svenruppert.flow.views.main;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.WorkshopDefaults;
import com.svenruppert.flow.views.glossary.GlossaryView;
import com.svenruppert.flow.views.module01.LlmConfig;
import com.svenruppert.flow.views.module01.Module01View;
import com.svenruppert.flow.views.module02.Module02View;
import com.svenruppert.flow.views.module03.Module03View;
import com.svenruppert.flow.views.module04.Module04View;
import com.svenruppert.flow.views.module05.Module05View;
import com.svenruppert.flow.views.module06.Module06View;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.component.dependency.CssImport;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Dashboard at {@code "/"}. Five stacked zones:
 * <ol>
 *   <li>Hero -- workshop title and a one-paragraph orientation.</li>
 *   <li>Big-picture SVG -- the RAG architecture that the six modules
 *       assemble.</li>
 *   <li>Module grid -- six clickable cards, accent-coloured to match
 *       the indexing (blue) and query (orange) zones of the big
 *       picture.</li>
 *   <li>System status -- a live probe of the configured Ollama
 *       instance, listing required and optional models.</li>
 *   <li>Resources and glossary -- external links plus a collapsible
 *       18-term reference.</li>
 * </ol>
 *
 * <p>The status zone runs its probes on a virtual thread on attach
 * so the view paints immediately, regardless of whether Ollama is
 * up. Every UI mutation goes through {@link UI#access}.
 */
@Route(value = MainView.PATH, layout = MainLayout.class)
@CssImport("./styles/dashboard.css")
public class MainView
    extends VerticalLayout
    implements HasLogger {

  public static final String PATH = "";

  // Accent palette aligned with the big-picture diagram's zones.
  private static final String ACCENT_NEUTRAL = "#64748B";
  private static final String ACCENT_INDEXING = "#0B72E7";
  private static final String ACCENT_QUERY = "#EA580C";
  private static final String ACCENT_PRODUCT = "#7C3AED";

  private static final List<String> REQUIRED_MODELS = WorkshopDefaults.REQUIRED_MODELS;
  private static final List<String> OPTIONAL_MODELS = WorkshopDefaults.OPTIONAL_MODELS;

  private final LlmConfig llmConfig = LlmConfig.defaults();
  private final OllamaProbe probe = new OllamaProbe(llmConfig.baseUrl());

  // Status zone containers -- repopulated by runDiagnostics().
  private final Div ollamaPanelBody = new Div();
  private final Div modelsPanelBody = new Div();
  private final Button refreshButton = new Button(VaadinIcon.REFRESH.create());

  public MainView() {
    setSizeFull();
    setPadding(false);
    setSpacing(false);
    addClassName("dashboard-root");

    add(buildHero());
    add(buildDivider());
    add(new RagBigPicture());
    add(buildDivider());
    add(buildModuleGrid());
    add(buildDivider());
    add(buildSystemZone());
    add(buildDivider());
    add(buildResourcesZone());

    refreshButton.setText(getTranslation("main.system.refresh"));
    refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
    refreshButton.addClickListener(e -> runDiagnostics());
  }

  @Override
  protected void onAttach(AttachEvent event) {
    super.onAttach(event);
    runDiagnostics();
  }

  private Component buildDivider() {
    Div d = new Div();
    d.addClassName("dashboard-divider");
    return d;
  }

  // ---------- zone 1: hero -------------------------------------------

  private Component buildHero() {
    Div wrap = new Div();
    wrap.addClassNames("dashboard-zone", "dashboard-hero");

    H1 heading = new H1(getTranslation("main.hero.title"));
    Paragraph sub = new Paragraph(getTranslation("main.hero.subtitle"));
    wrap.add(heading, sub);
    return wrap;
  }

  // ---------- zone 3: module grid ------------------------------------

  private Component buildModuleGrid() {
    Div zone = new Div();
    zone.addClassName("dashboard-zone");

    H2 heading = new H2(getTranslation("main.modules.heading"));
    Div grid = new Div();
    grid.addClassName("dashboard-grid");

    grid.add(
        new ModuleCard("M1", getTranslation("main.module1.title"),
            getTranslation("main.module1.desc"),
            ACCENT_NEUTRAL, Module01View.PATH),
        new ModuleCard("M2", getTranslation("main.module2.title"),
            getTranslation("main.module2.desc"),
            ACCENT_INDEXING, Module02View.PATH),
        new ModuleCard("M3", getTranslation("main.module3.title"),
            getTranslation("main.module3.desc"),
            ACCENT_INDEXING, Module03View.PATH),
        new ModuleCard("M4", getTranslation("main.module4.title"),
            getTranslation("main.module4.desc"),
            ACCENT_QUERY, Module04View.PATH),
        new ModuleCard("M5", getTranslation("main.module5.title"),
            getTranslation("main.module5.desc"),
            ACCENT_QUERY, Module05View.PATH),
        new ModuleCard("M6", getTranslation("main.module6.title"),
            getTranslation("main.module6.desc"),
            ACCENT_PRODUCT, Module06View.PATH));

    zone.add(heading, grid);
    return zone;
  }

  // ---------- zone 4: system status ----------------------------------

  private Component buildSystemZone() {
    Div zone = new Div();
    zone.addClassName("dashboard-zone");

    HorizontalLayout headerRow = new HorizontalLayout();
    headerRow.setAlignItems(FlexComponent.Alignment.CENTER);
    headerRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
    headerRow.setWidthFull();
    headerRow.setSpacing(true);
    H2 h = new H2(getTranslation("main.system.heading"));
    h.addClassName("dashboard-system-heading");
    headerRow.add(h, refreshButton);

    Div row = new Div();
    row.addClassNames("dashboard-system-row", "dashboard-system-body");
    row.add(buildOllamaPanel(), buildModelsPanel());

    zone.add(headerRow, new Div(row));
    return zone;
  }

  private Component buildOllamaPanel() {
    Div panel = new Div();
    panel.addClassName("dashboard-panel");
    H3 title = new H3(getTranslation("main.system.ollama.panel"));
    panel.add(title, ollamaPanelBody);
    return panel;
  }

  private Component buildModelsPanel() {
    Div panel = new Div();
    panel.addClassName("dashboard-panel");
    H3 title = new H3(getTranslation("main.system.models.panel"));
    panel.add(title, modelsPanelBody);
    return panel;
  }

  // ---------- diagnostics --------------------------------------------

  /**
   * Replaces both status panels with a "Checking..." placeholder, then
   * spawns a virtual thread to run the two probes. Every UI mutation
   * is marshalled back through {@link UI#access}; exceptions are
   * logged, never propagated.
   */
  private void runDiagnostics() {
    UI ui = UI.getCurrent();
    refreshButton.setEnabled(false);

    ollamaPanelBody.removeAll();
    ollamaPanelBody.add(checkingPlaceholder());
    modelsPanelBody.removeAll();
    modelsPanelBody.add(checkingPlaceholder());

    Thread.ofVirtual().name("dashboard-diagnostics-" + System.nanoTime()).start(() -> {
      Optional<String> version;
      try {
        version = probe.version();
      } catch (RuntimeException e) {
        logger().warn("Version probe threw: {}", e.toString());
        version = Optional.empty();
      }
      boolean reachable = version.isPresent();

      Optional<Map<String, String>> tags = reachable
          ? safeTags()
          : Optional.empty();

      Optional<String> finalVersion = version;
      ui.access(() -> {
        renderOllamaPanel(reachable, finalVersion);
        renderModelsPanel(reachable, tags.orElse(Map.of()));
        refreshButton.setEnabled(true);
      });
    });
  }

  private Optional<Map<String, String>> safeTags() {
    try {
      return probe.localModels();
    } catch (RuntimeException e) {
      logger().warn("Tags probe threw: {}", e.toString());
      return Optional.empty();
    }
  }

  private Component checkingPlaceholder() {
    HorizontalLayout row = new HorizontalLayout();
    row.setAlignItems(FlexComponent.Alignment.CENTER);
    row.setSpacing(true);
    ProgressBar bar = new ProgressBar();
    bar.setIndeterminate(true);
    bar.addClassName("dashboard-checking-bar");
    Span label = new Span(getTranslation("main.system.checking"));
    label.addClassName("dashboard-muted");
    row.add(bar, label);
    return row;
  }

  private void renderOllamaPanel(boolean reachable, Optional<String> version) {
    ollamaPanelBody.removeAll();
    if (reachable) {
      Icon ok = VaadinIcon.CHECK_CIRCLE.create();
      ok.addClassName("dashboard-status-ok");
      Span line = new Span(new Text(getTranslation("main.system.ollama.reachable", llmConfig.baseUrl())));
      HorizontalLayout row = new HorizontalLayout(ok, line);
      row.setAlignItems(FlexComponent.Alignment.CENTER);
      row.setSpacing(true);
      ollamaPanelBody.add(row);

      Paragraph versionLine = new Paragraph(getTranslation("main.system.ollama.version", version.orElse("(unknown)")));
      versionLine.addClassNames("dashboard-muted", "dashboard-ollama-detail");
      ollamaPanelBody.add(versionLine);
    } else {
      Icon warn = VaadinIcon.WARNING.create();
      warn.addClassName("dashboard-status-error");
      Span line = new Span(new Text(getTranslation("main.system.ollama.unreachable", llmConfig.baseUrl())));
      HorizontalLayout row = new HorizontalLayout(warn, line);
      row.setAlignItems(FlexComponent.Alignment.CENTER);
      row.setSpacing(true);
      ollamaPanelBody.add(row);

      Paragraph follow = new Paragraph(getTranslation("main.system.ollama.running"));
      follow.addClassNames("dashboard-muted", "dashboard-ollama-detail");
      ollamaPanelBody.add(follow);

      Anchor install = new Anchor("https://ollama.com/download", getTranslation("main.system.ollama.install"));
      install.setTarget(AnchorTarget.BLANK);
      install.getElement().setAttribute("rel", "noopener");
      install.addClassName("dashboard-link");
      Icon ext = VaadinIcon.EXTERNAL_LINK.create();
      ext.addClassName("dashboard-ext-icon");
      install.add(ext);
      ollamaPanelBody.add(install);
    }
  }

  private void renderModelsPanel(boolean reachable, Map<String, String> installed) {
    modelsPanelBody.removeAll();
    if (!reachable) {
      Paragraph offline = new Paragraph(getTranslation("main.system.models.offline"));
      offline.addClassNames("dashboard-muted", "dashboard-offline-note");
      modelsPanelBody.add(offline);
      return;
    }

    modelsPanelBody.add(buildModelBlock(getTranslation("main.system.models.required"), REQUIRED_MODELS, installed, true));
    Div spacer = new Div();
    spacer.addClassName("dashboard-model-spacer");
    modelsPanelBody.add(spacer);
    modelsPanelBody.add(buildModelBlock(getTranslation("main.system.models.optional"), OPTIONAL_MODELS, installed, false));
  }

  private Component buildModelBlock(String blockTitle,
                                    List<String> expected,
                                    Map<String, String> installed,
                                    boolean requiredBlock) {
    VerticalLayout box = new VerticalLayout();
    box.setPadding(false);
    box.setSpacing(false);
    H3 title = new H3(blockTitle);
    title.addClassName("dashboard-model-block-title");
    box.add(title);

    for (String name : expected) {
      String digest = findInstalledDigest(installed, name);
      boolean present = digest != null;
      box.add(buildModelRow(name, present, digest, requiredBlock));
    }
    return box;
  }

  /**
   * Ollama tag names commonly include a {@code :latest} suffix that
   * users don't type; accept either form when checking presence.
   */
  private String findInstalledDigest(Map<String, String> installed, String expected) {
    if (installed.containsKey(expected)) return installed.get(expected);
    String withLatest = expected + ":latest";
    if (installed.containsKey(withLatest)) return installed.get(withLatest);
    // Also accept when the expected name already carries a tag and the
    // installed list has the bare name.
    int colon = expected.indexOf(':');
    if (colon > 0) {
      String bare = expected.substring(0, colon);
      if (installed.containsKey(bare)) return installed.get(bare);
    }
    return null;
  }

  private Component buildModelRow(String name, boolean present,
                                  String digest, boolean requiredBlock) {
    HorizontalLayout row = new HorizontalLayout();
    row.setAlignItems(FlexComponent.Alignment.CENTER);
    row.setSpacing(true);
    row.addClassName("dashboard-model-row");
    if (!present && requiredBlock) {
      row.addClassName("dashboard-model-missing-required");
    }

    Icon icon;
    if (present) {
      icon = VaadinIcon.CHECK_CIRCLE.create();
      icon.addClassName("dashboard-status-ok");
    } else {
      icon = VaadinIcon.WARNING.create();
      icon.addClassName(requiredBlock ? "dashboard-status-warn" : "dashboard-status-optional");
    }

    VerticalLayout col = new VerticalLayout();
    col.setPadding(false);
    col.setSpacing(false);
    Span nameSpan = new Span(name);
    nameSpan.addClassName("dashboard-model-name");
    col.add(nameSpan);
    if (present && digest != null && !digest.isEmpty()) {
      Span d = new Span(digest);
      d.addClassName("dashboard-digest");
      col.add(d);
    }

    row.add(icon, col);
    row.setFlexGrow(1, col);

    if (!present) {
      Button copy = new Button(getTranslation("main.system.models.copy"), VaadinIcon.CLIPBOARD.create());
      copy.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
      copy.addClickListener(e -> copyToClipboard("ollama pull " + name));
      row.add(copy);
    }
    return row;
  }

  private void copyToClipboard(String text) {
    UI.getCurrent().getPage().executeJs(
        "navigator.clipboard && navigator.clipboard.writeText($0)", text);
    Notification.show(getTranslation("main.system.models.copied", text));
  }

  // ---------- zone 5: resources + glossary ---------------------------

  private Component buildResourcesZone() {
    Div zone = new Div();
    zone.addClassName("dashboard-zone");

    Div row = new Div();
    row.addClassName("dashboard-resources-row");
    row.add(buildLinksPanel());
    zone.add(row);
    return zone;
  }

  private Component buildLinksPanel() {
    Div panel = new Div();
    panel.addClassName("dashboard-panel");
    H3 title = new H3(getTranslation("main.links.panel"));
    panel.add(title);

    panel.add(internalLink(getTranslation("main.links.glossary"), GlossaryView.PATH, VaadinIcon.BOOK));
    panel.add(externalLink(getTranslation("main.links.ollama.docs"), "https://docs.ollama.com"));
    panel.add(externalLink(getTranslation("main.links.ollama.install"), "https://ollama.com/download"));
    panel.add(externalLink(getTranslation("main.links.urlshortener"), "https://github.com/svenruppert/url-shortener"));
    panel.add(externalLink(getTranslation("main.links.blog"), "https://svenruppert.com"));
    return panel;
  }

  private Component internalLink(String label, String route, VaadinIcon iconKind) {
    // RouterLink-equivalent navigation without the anchor's default
    // styling: a plain click handler on a Div, visually matching the
    // external links in the same panel but without an external-link
    // trailing icon.
    Div wrap = new Div();
    wrap.addClassName("dashboard-link");
    Icon leading = iconKind.create();
    leading.addClassName("dashboard-leading-icon");
    Span text = new Span(label);
    wrap.add(leading, text);
    wrap.addClickListener(e -> UI.getCurrent().navigate(route));
    return wrap;
  }

  private Component externalLink(String label, String href) {
    Anchor a = new Anchor(href, label);
    a.setTarget(AnchorTarget.BLANK);
    a.getElement().setAttribute("rel", "noopener");
    a.addClassName("dashboard-link");
    Icon ext = VaadinIcon.EXTERNAL_LINK.create();
    ext.addClassName("dashboard-ext-icon");
    a.add(ext);
    Div wrap = new Div(a);
    wrap.addClassName("dashboard-link-row");
    return wrap;
  }

}
