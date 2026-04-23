package com.svenruppert.flow.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Owns a session-scoped temporary directory for Upload payloads.
 *
 * <p>Wraps the boilerplate that lived inline in Module04View and
 * Module05View: create a temp dir in {@code onAttach}, resolve per-file
 * write paths for uploaded bytes, recursively delete the whole tree in
 * {@code onDetach}. Failures during cleanup are forwarded to a caller
 * supplied warning sink (typically the view's logger).
 *
 * <p>Lifecycle: {@link #create(String)} during attach, use
 * {@link #resolve(String)} per file, then {@link #close(Consumer)}
 * during detach. The instance is single-use -- after {@code close} the
 * directory is gone and the object should be discarded.
 */
public final class UploadTempDir {

  private final Path path;

  private UploadTempDir(Path path) {
    this.path = path;
  }

  /**
   * Creates a new temp directory with the given name prefix. Delegates
   * to {@link Files#createTempDirectory(String, java.nio.file.attribute.FileAttribute...)}
   * so the OS picks the location.
   *
   * @param prefix directory-name prefix, e.g. {@code "module04-upload-"}
   * @return a fresh {@code UploadTempDir} backed by the new directory
   * @throws IOException if the directory cannot be created
   */
  public static UploadTempDir create(final String prefix) throws IOException {
    return new UploadTempDir(Files.createTempDirectory(prefix));
  }

  /** Returns the backing {@link Path}. Prefer {@link #resolve} at call sites. */
  public Path path() {
    return path;
  }

  /** Resolves {@code name} against the managed directory. */
  public Path resolve(final String name) {
    return path.resolve(name);
  }

  /**
   * Recursively removes the managed directory. Best-effort: per-file
   * errors are swallowed, walk-level errors are routed to {@code warn}.
   * Safe to call on an already-deleted directory.
   */
  public void close(final Consumer<String> warn) {
    PathCleanup.deleteRecursively(path, warn);
  }
}
