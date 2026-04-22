package com.svenruppert.flow.views.help;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Html;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.Set;

/**
 * Shared Markdown rendering pipeline for model output and workshop
 * reference content. Raw HTML is escaped and links are sanitized before
 * the result is handed to Vaadin's Html component.
 */
public final class MarkdownSupport {

  private static final Set<Extension> EXTENSIONS = Set.of(TablesExtension.create());
  private static final Parser PARSER = Parser.builder()
      .extensions(EXTENSIONS)
      .build();
  private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
      .extensions(EXTENSIONS)
      .escapeHtml(true)
      .sanitizeUrls(true)
      .build();

  private MarkdownSupport() {
  }

  public static String renderSafeHtml(String markdown) {
    return RENDERER.render(PARSER.parse(markdown == null ? "" : markdown));
  }

  public static Html htmlDiv(String innerHtml) {
    return new Html("<div>" + (innerHtml == null ? "" : innerHtml) + "</div>");
  }

  public static void highlightCodeBlocks(Component host) {
    host.getElement().executeJs(
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
}
