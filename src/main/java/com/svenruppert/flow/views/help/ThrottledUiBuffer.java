package com.svenruppert.flow.views.help;

import com.vaadin.flow.component.UI;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Accumulates streamed text and pushes snapshots to a Vaadin UI at a
 * bounded cadence. The caller remains responsible for final rendering
 * after the stream completes.
 */
public final class ThrottledUiBuffer {

  private final UI ui;
  private final long minIntervalMillis;
  private final Consumer<String> snapshotConsumer;
  private final StringBuilder buffer = new StringBuilder();
  private long lastPushMillis;

  public ThrottledUiBuffer(UI ui,
                           long minIntervalMillis,
                           Consumer<String> snapshotConsumer) {
    this.ui = Objects.requireNonNull(ui, "ui");
    if (minIntervalMillis < 0) {
      throw new IllegalArgumentException(
          "minIntervalMillis must be >= 0, got " + minIntervalMillis);
    }
    this.minIntervalMillis = minIntervalMillis;
    this.snapshotConsumer = Objects.requireNonNull(
        snapshotConsumer, "snapshotConsumer");
  }

  public void append(String text) {
    if (text == null || text.isEmpty()) return;
    buffer.append(text);
    long now = System.currentTimeMillis();
    if (now - lastPushMillis >= minIntervalMillis) {
      push(now);
    }
  }

  public void flush() {
    push(System.currentTimeMillis());
  }

  public String text() {
    return buffer.toString();
  }

  private void push(long now) {
    lastPushMillis = now;
    String snapshot = buffer.toString();
    ui.access(() -> snapshotConsumer.accept(snapshot));
  }
}
