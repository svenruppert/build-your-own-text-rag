package com.svenruppert.flow;

import com.vaadin.flow.i18n.I18NProvider;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Loads translations from {@code vaadin-i18n/translations[_LANG].properties}.
 * Supported locales: English (default) and German.
 * Falls back to English when the requested locale is not available.
 * Falls back to the key itself when a key is missing.
 */
public class AppI18NProvider implements I18NProvider {

  private static final String BUNDLE = "vaadin-i18n/translations";

  @Override
  public List<Locale> getProvidedLocales() {
    return List.of(Locale.ENGLISH, Locale.GERMAN);
  }

  @Override
  public String getTranslation(String key, Locale locale, Object... params) {
    ResourceBundle bundle = loadBundle(locale);
    if (!bundle.containsKey(key)) {
      return key;
    }
    String raw = bundle.getString(key);
    return params.length == 0 ? raw : MessageFormat.format(raw, params);
  }

  private ResourceBundle loadBundle(Locale locale) {
    try {
      return ResourceBundle.getBundle(BUNDLE, locale);
    } catch (MissingResourceException e) {
      return ResourceBundle.getBundle(BUNDLE, Locale.ENGLISH);
    }
  }
}
