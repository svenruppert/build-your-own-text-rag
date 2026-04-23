package com.svenruppert.flow.views.help;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.UploadHandler;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * A reusable "pending upload" composite: a compact {@link Upload}, a
 * chip strip of not-yet-ingested file names, and an Ingest button.
 *
 * <p>Replaces the byte-identical upload-+-chips plumbing that lived
 * inline in {@code Module04View} and {@code Module05View}. Both views
 * now supply per-module i18n labels and an ingest-button click handler
 * and then delegate the drain loop through {@link #drain}.
 *
 * <p>Intentional constraints shared across both callers (txt/md only,
 * max 10 files, the {@code compact-upload} CSS class) are baked in.
 * Callers that need different limits can still reach the underlying
 * widgets via {@link #uploadComponent()} and {@link #ingestButton()}.
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 * PendingUploadZone zone = new PendingUploadZone(new PendingUploadZone.Labels(
 *     getTranslation("m04.upload.drop"),
 *     getTranslation("m04.chip.empty"),
 *     getTranslation("m04.chip.remove.title"),
 *     getTranslation("m04.button.ingest")));
 * zone.ingestButton().addClickListener(e -> onIngest());
 *
 * void onIngest() {
 *   zone.drain(
 *       (fileName, bytes) -> {
 *         Path written = uploadTempDir.resolve(fileName);
 *         Files.write(written, bytes);
 *         pipeline.ingest(loader.load(written));
 *         processed++;
 *       },
 *       (fileName, msg) -> Notification.show(
 *           getTranslation("m04.error.ingest", fileName, msg)));
 * }
 * }</pre>
 */
public final class PendingUploadZone extends HorizontalLayout {

  /** i18n labels the zone needs at construction time. */
  public record Labels(String dropText,
                       String chipEmpty,
                       String chipRemoveTitle,
                       String ingestButtonText) { }

  /** Per-entry processor; may throw {@link IOException}. */
  @FunctionalInterface
  public interface EntryHandler {
    /**
     * Processes a single uploaded entry. Thrown {@link IOException}s
     * are forwarded to the {@code onIOError} sink in {@link #drain}.
     */
    void accept(String fileName, byte[] bytes) throws IOException;
  }

  private final Set<String> pendingFilenames = new LinkedHashSet<>();
  private final Map<String, byte[]> pendingBytes = new LinkedHashMap<>();
  private final HorizontalLayout chips = new HorizontalLayout();
  private final Button ingestButton = new Button();
  private final Upload upload;
  private final Labels labels;

  public PendingUploadZone(final Labels labels) {
    this.labels = labels;
    this.upload = new Upload(UploadHandler.inMemory((metadata, bytes) -> {
      pendingBytes.put(metadata.fileName(), bytes);
      pendingFilenames.add(metadata.fileName());
      renderChips();
    }));
    upload.setAcceptedFileTypes("text/plain", "text/markdown", ".txt", ".md", ".markdown");
    upload.setMaxFiles(10);
    // Compact visual: fixed width via CSS, internal file list hidden --
    // we render our own chip row next to it.
    upload.addClassName("compact-upload");
    upload.setDropLabel(new Span(labels.dropText()));

    chips.addClassName("pending-chips");
    chips.setSpacing(false);
    renderChips();

    ingestButton.setText(labels.ingestButtonText());

    add(upload, chips, ingestButton);
    setAlignItems(FlexComponent.Alignment.CENTER);
    setSpacing(true);
    setWidthFull();
    setFlexGrow(1, chips);
  }

  /** The Ingest button. Wire your click listener and enable/disable from outside. */
  public Button ingestButton() {
    return ingestButton;
  }

  /** Escape hatch for tests or callers who need to tweak the underlying Upload. */
  public Upload uploadComponent() {
    return upload;
  }

  /** Whether at least one file is queued and not yet drained. */
  public boolean hasPending() {
    return !pendingFilenames.isEmpty();
  }

  /** Read-only view of the currently queued file names, in upload order. */
  public Set<String> pendingFilenames() {
    return Set.copyOf(pendingFilenames);
  }

  /**
   * Iterates the queue, invoking {@code handler} once per file and
   * removing the entry from the queue afterwards (success or failure).
   * Re-renders the chip strip at the end so the caller doesn't have to.
   *
   * @param handler    per-entry work; may throw {@link IOException}
   * @param onIOError  receives {@code (fileName, message)} for each
   *                   {@link IOException} caught from {@code handler};
   *                   may be {@code null} to ignore
   */
  public void drain(final EntryHandler handler,
                    final BiConsumer<String, String> onIOError) {
    // Snapshot the key set so the finally-remove can mutate safely.
    for (String fileName : List.copyOf(pendingFilenames)) {
      byte[] bytes = pendingBytes.get(fileName);
      if (bytes == null) {
        pendingFilenames.remove(fileName);
        continue;
      }
      try {
        handler.accept(fileName, bytes);
      } catch (IOException e) {
        if (onIOError != null) {
          onIOError.accept(fileName, e.getMessage());
        }
      } finally {
        pendingFilenames.remove(fileName);
        pendingBytes.remove(fileName);
      }
    }
    renderChips();
  }

  /** Drops the queue and re-renders. Does not touch the Upload widget state. */
  public void clear() {
    pendingFilenames.clear();
    pendingBytes.clear();
    renderChips();
  }

  private void renderChips() {
    chips.removeAll();
    if (pendingFilenames.isEmpty()) {
      Span empty = new Span(labels.chipEmpty());
      empty.addClassName("pending-chip-empty");
      chips.add(empty);
      return;
    }
    for (String name : pendingFilenames) {
      Span chip = new Span(name + "  \u00D7");
      chip.addClassName("pending-chip");
      chip.getElement().setAttribute("title", labels.chipRemoveTitle());
      chip.addClickListener(e -> {
        pendingFilenames.remove(name);
        renderChips();
      });
      chips.add(chip);
    }
  }
}
