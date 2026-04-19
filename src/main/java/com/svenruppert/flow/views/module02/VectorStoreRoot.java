package com.svenruppert.flow.views.module02;

import java.util.HashMap;
import java.util.Map;

/**
 * The persistent root object held by EclipseStore.
 *
 * <p>It keeps exactly one field: the id → {@link RawVectorEntry} map.
 * EclipseStore persists the entire reachable object graph rooted here.
 * No schema migration, no version field, no auxiliary metadata -- the
 * workshop version stays intentionally minimal.
 *
 * <p>Note on the backing collection: the module spec sketched a
 * {@code GigaMap<String, RawVectorEntry>}, but the real
 * {@code org.eclipse.store.gigamap.types.GigaMap} has a single type
 * parameter ({@code GigaMap<E>}) and is not a {@code java.util.Map}.
 * A standard {@link HashMap} keeps the code readable for participants
 * while preserving the didactic point: EclipseStore persists raw
 * embedding vectors durably, and the HNSW graph is rebuilt in memory
 * from them on start-up.
 */
public final class VectorStoreRoot {

    private final Map<String, RawVectorEntry> entries = new HashMap<>();

    public Map<String, RawVectorEntry> entries() {
        return entries;
    }
}
