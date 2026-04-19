package com.svenruppert.flow.views.module02;

import java.util.Objects;

/**
 * A single hit from {@link VectorStore#search(float[], int)}.
 *
 * <p>The hit carries the stored id, the opaque payload string, and the
 * cosine-similarity score against the query vector. The vector itself is
 * intentionally not part of the result: the application never needs it --
 * payload is enough -- and omitting it avoids copying large float arrays
 * out of the store on every hit.
 *
 * @param id      store-assigned identifier; never {@code null}
 * @param payload opaque payload associated with {@code id}; may be empty but never {@code null}
 * @param score   cosine similarity in {@code [-1, 1]}; higher is closer
 */
public record SearchHit(String id, String payload, double score) {

    public SearchHit {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(payload, "payload");
        if (Double.isNaN(score) || score < -1.0 || score > 1.0) {
            throw new IllegalArgumentException(
                    "score must be in [-1, 1], got " + score);
        }
    }
}
