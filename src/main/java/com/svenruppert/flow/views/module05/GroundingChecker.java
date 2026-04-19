package com.svenruppert.flow.views.module05;

import com.svenruppert.flow.views.module04.RetrievalHit;

import java.util.List;

/**
 * Second-pass check: does the generated answer follow from the
 * retrieved chunks?
 *
 * <p>Invoked optionally by the {@link RagPipeline} after the
 * generator finishes. A checker must never throw -- an unparseable
 * reply should surface as a {@link GroundingResult.Verdict#UNKNOWN}
 * verdict rather than an exception.
 */
public interface GroundingChecker {

    GroundingResult check(String query, String answer,
                          List<RetrievalHit> hits, String model);
}
