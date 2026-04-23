package com.svenruppert.flow.views.help;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads locale-specific HTML fragment files for inline help panels.
 *
 * <p>Files live at {@code src/main/resources/help/{lang}/{name}.html}.
 * When no file exists for the requested language the loader falls back
 * to the English version. If that is also absent an empty string is
 * returned so the panel renders without crashing.
 *
 * <p>Results are cached per resource path in a
 * {@link ConcurrentHashMap}. Help fragments are packaged resources and
 * immutable for the lifetime of the JVM, so the cache never needs
 * invalidation. This removes repeated classloader lookups for panels
 * that are built each time a view is attached. The cache stores both
 * hits and misses (the latter via a sentinel) so English-fallback and
 * truly-missing entries are resolved without further I/O.
 */
public final class HelpLoader {

  /** Sentinel stored for paths the classloader reported as missing. */
  private static final String MISSING = "\0MISSING\0";

  private static final ConcurrentHashMap<String, String> CACHE =
      new ConcurrentHashMap<>();

  private HelpLoader() { }

  /**
   * Returns the HTML fragment for {@code name} in the given locale,
   * falling back to English if no locale-specific file is found.
   *
   * @param name   base file name without extension, e.g. {@code "m1_model"}
   * @param locale the current UI locale; {@code null} treated as English
   * @return HTML fragment string, or empty string if no file is found
   */
  public static String loadHtml(final String name, final Locale locale) {
    String lang = (locale != null) ? locale.getLanguage() : "en";
    String html = lookup("help/" + lang + "/" + name + ".html");
    if (html == null && !"en".equals(lang)) {
      html = lookup("help/en/" + name + ".html");
    }
    return html != null ? html : "";
  }

  /**
   * Returns the cached content for {@code path}, or loads and caches
   * it on first access. Returns {@code null} when the classloader has
   * no such resource (cached as a sentinel so subsequent calls don't
   * re-issue the lookup).
   */
  private static String lookup(final String path) {
    String cached = CACHE.get(path);
    if (cached != null) {
      return cached == MISSING ? null : cached;
    }
    String loaded = tryLoad(path);
    CACHE.put(path, loaded != null ? loaded : MISSING);
    return loaded;
  }

  private static String tryLoad(final String path) {
    ClassLoader cl = HelpLoader.class.getClassLoader();
    try (InputStream in = cl.getResourceAsStream(path)) {
      if (in == null) {
        return null;
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      return null;
    }
  }
}
