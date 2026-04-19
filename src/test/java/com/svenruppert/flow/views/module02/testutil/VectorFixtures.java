package com.svenruppert.flow.views.module02.testutil;

import com.svenruppert.flow.views.module02.RawVectorEntry;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Loads the deterministic dim-8 fixture under
 * {@code /module02/fixtures/vectors.json}. Ten entries in four logical
 * clusters (A near {@code e_0}, B near {@code e_1}, C near {@code e_4},
 * plus an orthogonal/noise pair). The values are hand-picked so top-k
 * assertions in the contract tests are both deterministic and easy to
 * reason about on paper.
 */
public final class VectorFixtures {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private VectorFixtures() {
    }

    public static List<RawVectorEntry> load() {
        try (InputStream in = VectorFixtures.class.getResourceAsStream(
                "/module02/fixtures/vectors.json")) {
            if (in == null) {
                throw new IllegalStateException(
                        "missing fixture /module02/fixtures/vectors.json on test classpath");
            }
            List<RawVectorEntry> entries =
                    MAPPER.readerForListOf(RawVectorEntry.class).readValue(in);
            return List.copyOf(entries);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the fixture entry with the given id, or throws if absent.
     * Handy for targeted assertions in tests.
     */
    public static RawVectorEntry byId(String id) {
        return load().stream()
                .filter(e -> e.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no fixture entry " + id));
    }
}
