package com.svenruppert.flow.views.help;

import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Div;

/**
 * Collapsible side channel for reasoning tokens. During streaming it
 * shows raw text; after completion the caller can replace that with
 * Markdown-rendered HTML.
 */
public final class ThinkingPanel {

  private final Div body = new Div();
  private final Details details = new Details("Thinking", body);
  private boolean revealed;

  public ThinkingPanel() {
    body.addClassName("thinking-box");
    details.addClassName("thinking-details");
    details.setOpened(false);
    details.setVisible(false);
  }

  public Details component() {
    return details;
  }

  public void resetForStreaming() {
    body.removeAll();
    body.setText("");
    body.addClassName("streaming");
    details.setSummaryText("Thinking");
    details.setOpened(false);
    details.setVisible(false);
    revealed = false;
  }

  public void showStreaming(String snapshot) {
    if (!revealed) {
      details.setVisible(true);
      details.setOpened(true);
      revealed = true;
    }
    body.setText(snapshot);
    details.setSummaryText("Thinking (" + snapshot.length() + " chars)");
  }

  public void finalise(String thinking, String renderedHtml) {
    if (thinking == null || thinking.isEmpty()) {
      details.setVisible(false);
      return;
    }
    body.removeAll();
    body.setText("");
    body.removeClassName("streaming");
    body.add(MarkdownSupport.htmlDiv(renderedHtml));
    details.setSummaryText("Thinking (" + thinking.length() + " chars)");
    details.setVisible(true);
    details.setOpened(false);
    revealed = true;
  }
}
