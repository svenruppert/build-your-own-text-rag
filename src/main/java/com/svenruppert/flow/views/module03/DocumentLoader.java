package com.svenruppert.flow.views.module03;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Turns a file on disk into a {@link Document}.
 *
 * <p>Implementations are file-type aware: the selection of a plain-text
 * vs. a structure-aware loader path is driven by the file extension (or,
 * as a future refinement, by magic bytes). Unknown types fall back to
 * UTF-8 plain-text decoding so ingestion never fails on a surprise
 * extension.
 */
public interface DocumentLoader {

    /**
     * Loads the file at {@code path} and returns a {@link Document}
     * with the extracted plain text plus any structural metadata the
     * underlying format exposes.
     *
     * @throws IOException on any I/O failure
     */
    Document load(Path path) throws IOException;
}
