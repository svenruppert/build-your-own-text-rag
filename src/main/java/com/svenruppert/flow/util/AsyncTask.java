package com.svenruppert.flow.util;

import com.vaadin.flow.component.UI;

import java.util.function.Consumer;

/**
 * Thin wrapper around the virtual-thread + {@link UI#access} pattern
 * used by the RAG views. Centralises the skeleton
 *
 * <pre>{@code
 * Thread.ofVirtual().name(...).start(() -> {
 *   try { work(); }
 *   catch (RuntimeException e) {
 *     logger().warn(...);
 *     ui.access(() -> errorUi(e));
 *   }
 * });
 * }</pre>
 *
 * so callers stop re-implementing the try/catch + ui-access dispatch
 * around every long-running worker.
 *
 * <p>Success UI pushes remain the worker's responsibility -- the
 * helper does not capture a return value or re-enter the UI on the
 * happy path. The rationale: the existing workers fire multiple
 * {@link UI#access} calls to stream progress, so pretending there is a
 * single "onSuccess" step would be a lie.
 */
public final class AsyncTask {

  private AsyncTask() { }

  /**
   * Runs {@code work} on a fresh named virtual thread. On
   * {@link RuntimeException} the exception is forwarded through
   * {@link UI#access} to {@code onError}; the caller's handler owns
   * logging, user-facing notifications and UI state reset (buttons,
   * progress bars, ...) with the session lock held.
   *
   * @param ui         the UI whose session state the callbacks mutate
   * @param threadName thread name prefix (a nanoTime suffix is appended)
   * @param work       the worker body
   * @param onError    error handler; runs inside {@link UI#access}
   */
  public static void runInBackground(final UI ui,
                                     final String threadName,
                                     final Runnable work,
                                     final Consumer<RuntimeException> onError) {
    Thread.ofVirtual()
        .name(threadName + "-" + System.nanoTime())
        .start(() -> {
          try {
            work.run();
          } catch (RuntimeException e) {
            ui.access(() -> onError.accept(e));
          }
        });
  }
}
