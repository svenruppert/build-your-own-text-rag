package com.svenruppert.flow.views.module02;

import java.io.Serializable;
import java.util.Objects;

/**
 * Persistent value type for the EclipseStore-backed store: an id, its
 * raw embedding vector and the opaque payload. Kept intentionally simple
 * -- no metadata, no timestamps -- so that serialisation and schema
 * evolution remain trivial for the workshop.
 *
 * <p>Implements {@link Serializable} because Vaadin may serialise the
 * UI session, and instances of this record can end up referenced from a
 * view's field while a store is held in-memory.
 */
public record RawVectorEntry(String id, float[] vector, String payload)
        implements Serializable {

    public RawVectorEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(vector, "vector");
        Objects.requireNonNull(payload, "payload");
        if (vector.length == 0) {
            throw new IllegalArgumentException("vector must not be empty");
        }
    }
}
