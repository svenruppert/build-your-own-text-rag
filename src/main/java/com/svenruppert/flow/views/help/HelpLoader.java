package com.svenruppert.flow.views.help;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Loads locale-specific HTML fragment files for inline help panels.
 *
 * <p>Files live at {@code src/main/resources/help/{lang}/{name}.html}.
 * When no file exists for the requested language the loader falls back
 * to the English version. If that is also absent an empty string is
 * returned so the panel renders without crashing.
 */
public final class HelpLoader {

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
    String html = tryLoad("help/" + lang + "/" + name + ".html");
    if (html == null && !"en".equals(lang)) {
      html = tryLoad("help/en/" + name + ".html");
    }
    return html != null ? html : "";
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
