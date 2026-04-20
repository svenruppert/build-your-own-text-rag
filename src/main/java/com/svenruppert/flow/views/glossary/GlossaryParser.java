package com.svenruppert.flow.views.glossary;

import com.svenruppert.dependencies.core.logger.HasLogger;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Document;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads and parses {@code /glossary/glossary.md} from the classpath
 * into a list of {@link GlossaryEntry} values. Parsed once per JVM;
 * subsequent calls return the cached result.
 *
 * <p>Every failure path (missing resource, I/O error, malformed
 * Markdown) is logged at {@code WARN} and returns whatever was
 * parsed successfully -- callers are expected to treat an empty list
 * as "content unavailable" and render an appropriate fallback rather
 * than to guard against exceptions here.
 */
public final class GlossaryParser implements HasLogger {

  private static final String RESOURCE_PATH = "/glossary/glossary.md";

  /** Matches a trailing {@code {#anchor-id}} suffix on a heading text. */
  private static final Pattern ANCHOR_SUFFIX =
      Pattern.compile("^(.*?)\\s*\\{#([^}]+)\\}\\s*$");

  /** Matches fragment hrefs in rendered body HTML. */
  private static final Pattern FRAGMENT_HREF =
      Pattern.compile("href=\"#([^\"]+)\"");

  /** Rendered result of a single parse pass -- entries plus intro HTML. */
  public record Parsed(List<GlossaryEntry> entries, String introHtml) {
    public Parsed {
      entries = List.copyOf(entries);
      introHtml = Objects.requireNonNull(introHtml);
    }
  }

  private static volatile Parsed cached;

  private GlossaryParser() {
  }

  /** Returns the cached parse result, loading it on first call. */
  public static Parsed load() {
    Parsed local = cached;
    if (local == null) {
      synchronized (GlossaryParser.class) {
        local = cached;
        if (local == null) {
          local = new GlossaryParser().parseOnce();
          cached = local;
        }
      }
    }
    return local;
  }

  private Parsed parseOnce() {
    String markdown = readResource();
    if (markdown == null) {
      return new Parsed(Collections.emptyList(), "");
    }
    try {
      Parser parser = Parser.builder().build();
      HtmlRenderer renderer = HtmlRenderer.builder()
          .escapeHtml(true)
          .sanitizeUrls(true)
          .build();
      Document doc = (Document) parser.parse(markdown);

      List<GlossaryEntry> entries = new ArrayList<>();
      String introHtml = extractIntroHtml(doc, renderer);
      GlossaryEntry.Section currentSection = null;

      Heading pendingHeading = null;
      List<Node> pendingBody = new ArrayList<>();

      Node node = doc.getFirstChild();
      while (node != null) {
        Node next = node.getNext();
        if (node instanceof Heading h) {
          if (h.getLevel() == 2) {
            flushEntry(entries, currentSection, pendingHeading, pendingBody, renderer);
            pendingHeading = null;
            pendingBody.clear();
            currentSection = classifySection(plainText(h));
          } else if (h.getLevel() == 3) {
            flushEntry(entries, currentSection, pendingHeading, pendingBody, renderer);
            pendingHeading = h;
            pendingBody.clear();
          }
          // level-1 heading (# Glossary) and any others are ignored
        } else {
          if (pendingHeading != null) {
            pendingBody.add(node);
          }
        }
        node = next;
      }
      flushEntry(entries, currentSection, pendingHeading, pendingBody, renderer);

      if (entries.isEmpty()) {
        logger().warn("Glossary parsed, but no entries were extracted. "
            + "Check the Markdown structure (expected ## Concepts / ## Tools "
            + "with ### Term {{#anchor}} entries).");
      }
      return new Parsed(entries, introHtml);
    } catch (RuntimeException e) {
      logger().warn("Glossary parsing failed: {}", e.toString());
      return new Parsed(Collections.emptyList(), "");
    }
  }

  private String readResource() {
    try (InputStream in = GlossaryParser.class.getResourceAsStream(RESOURCE_PATH)) {
      if (in == null) {
        logger().warn("Glossary resource not found on classpath: {}", RESOURCE_PATH);
        return null;
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      logger().warn("Could not read glossary resource {}: {}", RESOURCE_PATH, e.toString());
      return null;
    }
  }

  /**
   * Captures the intro HTML: the first Paragraph that appears after
   * the {@code # Glossary} heading and before any {@code ##} heading.
   * Returns the empty string when the intro cannot be located.
   */
  private String extractIntroHtml(Document doc, HtmlRenderer renderer) {
    Node node = doc.getFirstChild();
    boolean seenH1 = false;
    while (node != null) {
      if (node instanceof Heading h) {
        if (h.getLevel() == 1) {
          seenH1 = true;
        } else if (h.getLevel() >= 2) {
          return "";
        }
      } else if (seenH1 && node instanceof Paragraph) {
        return renderer.render(node);
      }
      node = node.getNext();
    }
    return "";
  }

  private GlossaryEntry.Section classifySection(String headingText) {
    String lower = headingText.toLowerCase();
    if (lower.contains("concept")) return GlossaryEntry.Section.CONCEPTS;
    if (lower.contains("tool")) return GlossaryEntry.Section.TOOLS;
    return null;
  }

  private void flushEntry(List<GlossaryEntry> entries,
                          GlossaryEntry.Section section,
                          Heading heading,
                          List<Node> body,
                          HtmlRenderer renderer) {
    if (heading == null) return;
    if (section == null) {
      // Entry appeared outside any section -- skip it quietly rather
      // than guess, but mention it so the author can fix the source.
      logger().warn("Skipping glossary entry '{}' -- not inside a ## section.",
          plainText(heading));
      return;
    }
    String rawTitle = plainText(heading);
    Matcher m = ANCHOR_SUFFIX.matcher(rawTitle);
    String title;
    String anchor;
    if (m.matches()) {
      title = m.group(1).trim();
      anchor = m.group(2).trim();
    } else {
      title = rawTitle.trim();
      anchor = slugify(title);
      logger().warn("Glossary entry '{}' has no {{#anchor}} marker -- "
          + "falling back to slug '{}'.", title, anchor);
    }
    String bodyHtml = renderBody(body, renderer);
    List<String> refs = extractFragmentTargets(bodyHtml);
    entries.add(new GlossaryEntry(anchor, title, bodyHtml, refs, section));
  }

  private String renderBody(List<Node> nodes, HtmlRenderer renderer) {
    if (nodes.isEmpty()) return "";
    StringBuilder sb = new StringBuilder();
    for (Node n : nodes) {
      sb.append(renderer.render(n));
    }
    return sb.toString();
  }

  private List<String> extractFragmentTargets(String html) {
    LinkedHashSet<String> anchors = new LinkedHashSet<>();
    Matcher m = FRAGMENT_HREF.matcher(html);
    while (m.find()) {
      anchors.add(m.group(1));
    }
    return new ArrayList<>(anchors);
  }

  /**
   * Concatenates the {@link Text} literals inside a heading, preserving
   * spacing. Emphasis, code and link nodes are flattened to their
   * visible text -- this matches what the author typed.
   */
  private String plainText(Node node) {
    StringBuilder sb = new StringBuilder();
    node.accept(new AbstractVisitor() {
      @Override
      public void visit(Text text) {
        sb.append(text.getLiteral());
      }
    });
    return sb.toString();
  }

  private String slugify(String title) {
    return title.toLowerCase().trim()
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("^-|-$", "");
  }
}
