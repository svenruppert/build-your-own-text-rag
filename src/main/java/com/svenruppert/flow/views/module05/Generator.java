package com.svenruppert.flow.views.module05;

import com.svenruppert.flow.views.module04.RetrievalHit;

import java.util.List;
import java.util.function.Consumer;

/**
 * Turns a query plus retrieved chunks into a grounded answer.
 *
 * <p>The {@code tokenSink} {@link Consumer} is invoked on each token
 * as it arrives from the model -- the Module 05 view uses this hook
 * to stream tokens into the DOM via {@code UI.access()}. The sink is
 * called from the same thread that runs {@link #generate}, which is
 * typically a worker thread outside the UI-lock; implementations MUST
 * call the sink in the order they see the tokens. They MAY batch
 * tokens if the transport delivers them coalesced -- Ollama sometimes
 * emits multi-character chunks.
 *
 * <p>A {@code null} tokenSink is treated as a no-op: callers that do
 * not care about streaming can pass {@code null} without branching.
 */
public interface Generator {

    GeneratedAnswer generate(String query,
                             List<RetrievalHit> hits,
                             String model,
                             Consumer<String> tokenSink);
}
