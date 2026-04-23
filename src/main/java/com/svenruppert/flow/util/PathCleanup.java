package com.svenruppert.flow.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Best-effort recursive directory deletion.
 *
 * <p>Replaces four byte-identical private copies that lived in
 * {@code Module02View}, {@code Module04View}, {@code Module05View} and
 * {@code JVectorStoreBenchmark}. The semantics intentionally match the
 * originals:
 * <ul>
 *   <li>a {@code null} root is a no-op,</li>
 *   <li>per-file deletion failures are swallowed (cleanup is
 *       best-effort; callers routinely run this during UI detach or
 *       benchmark teardown where a straggling temp file is preferable
 *       to a failure),</li>
 *   <li>a walk-level {@link IOException} is forwarded to the optional
 *       {@code warn} sink so the caller can log it through its own
 *       logger without this utility taking an SLF4J dependency.</li>
 * </ul>
 *
 * <p>Call sites typically wire the sink to their logger, e.g.
 * {@code PathCleanup.deleteRecursively(dir, msg -> logger().warn(msg));}.
 * Pass {@code null} to suppress the warning entirely.
 */
public final class PathCleanup {

  private PathCleanup() {
    // utility class
  }

  /**
   * Deletes {@code root} and every file/directory beneath it in
   * reverse order (children before parents).
   *
   * @param root directory to remove; {@code null} is ignored
   * @param warn optional sink for the walk-level error message;
   *             may be {@code null}
   */
  public static void deleteRecursively(Path root, Consumer<String> warn) {
    if (root == null) return;
    try (Stream<Path> walk = Files.walk(root)) {
      walk.sorted(Comparator.reverseOrder()).forEach(p -> {
        try {
          Files.deleteIfExists(p);
        } catch (IOException ignored) {
          // best-effort cleanup
        }
      });
    } catch (IOException e) {
      if (warn != null) {
        warn.accept("Could not delete " + root + ": " + e.getMessage());
      }
    }
  }
}
