package com.svenruppert.flow.views.module05;

import com.svenruppert.flow.views.module04.RetrievalHit;

import java.util.List;
import java.util.function.Consumer;

/**
 * Turns a query plus retrieved chunks into a grounded answer.
 *
 * <p>Two overloads:
 * <ul>
 *   <li>The 4-argument {@link #generate(String, List, String, Consumer)}
 *       is the stable contract every implementation must provide. Its
 *       {@code tokenSink} receives user-facing answer tokens only.</li>
 *   <li>The 5-argument
 *       {@link #generate(String, List, String, Consumer, Consumer)} adds
 *       a separate {@code thinkingSink} for the reasoning tokens a
 *       thinking model emits (see {@link StreamEvent.Thinking}). The
 *       default implementation delegates to the 4-argument variant,
 *       losing the thinking channel. Implementations that speak to a
 *       thinking-aware backend should override this.</li>
 * </ul>
 *
 * <p>Sinks are invoked on the same thread that runs
 * {@code generate()}, typically a worker thread outside the UI lock.
 * A {@code null} sink is treated as a no-op consumer.
 */
public interface Generator {

    GeneratedAnswer generate(String query,
                             List<RetrievalHit> hits,
                             String model,
                             Consumer<String> tokenSink);

    /**
     * Thinking-aware variant. Default implementation delegates to the
     * 4-argument version; override for models that separate reasoning
     * tokens from answer tokens.
     */
    default GeneratedAnswer generate(String query,
                                     List<RetrievalHit> hits,
                                     String model,
                                     Consumer<String> tokenSink,
                                     Consumer<String> thinkingSink) {
        return generate(query, hits, model, tokenSink);
    }
}
