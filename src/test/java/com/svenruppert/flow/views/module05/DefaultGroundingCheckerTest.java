package com.svenruppert.flow.views.module05;

import com.svenruppert.flow.views.module04.RetrievalHit;
import com.svenruppert.flow.views.module04.testutil.StubLlmClient;
import com.svenruppert.flow.views.module04.testutil.TestChunks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultGroundingCheckerTest {

    private static final String MODEL = "stub-model";

    private static List<RetrievalHit> hits() {
        return List.of(new RetrievalHit(TestChunks.of(0, "ctx"), 0.9, "vector"));
    }

    @Test
    @DisplayName("parses VERDICT: GROUNDED")
    void check_parsesGroundedVerdict() {
        StubLlmClient llm = new StubLlmClient()
                .withGenerate("=== ANSWER ===",
                        "VERDICT: GROUNDED\nRATIONALE: Every claim is supported.");
        GroundingResult out = new DefaultGroundingChecker(llm)
                .check("q", "a", hits(), MODEL);
        assertEquals(GroundingResult.Verdict.GROUNDED, out.verdict());
        assertEquals("Every claim is supported.", out.rationale());
    }

    @Test
    @DisplayName("parses VERDICT: PARTIAL")
    void check_parsesPartialVerdict() {
        StubLlmClient llm = new StubLlmClient()
                .withGenerate("=== ANSWER ===",
                        "VERDICT: PARTIAL\nRATIONALE: Some claims are supported, others are not.");
        GroundingResult out = new DefaultGroundingChecker(llm)
                .check("q", "a", hits(), MODEL);
        assertEquals(GroundingResult.Verdict.PARTIAL, out.verdict());
        assertTrue(out.rationale().startsWith("Some claims"));
    }

    @Test
    @DisplayName("parses VERDICT: NOT_GROUNDED")
    void check_parsesNotGroundedVerdict() {
        StubLlmClient llm = new StubLlmClient()
                .withGenerate("=== ANSWER ===",
                        "VERDICT: NOT_GROUNDED\nRATIONALE: The answer invents facts.");
        GroundingResult out = new DefaultGroundingChecker(llm)
                .check("q", "a", hits(), MODEL);
        assertEquals(GroundingResult.Verdict.NOT_GROUNDED, out.verdict());
    }

    @Test
    @DisplayName("returns UNKNOWN on unparseable reply")
    void check_returnsUnknownOnUnparseableReply() {
        StubLlmClient llm = new StubLlmClient()
                .withGenerate("=== ANSWER ===", "I would say the answer looks reasonable.");
        GroundingResult out = new DefaultGroundingChecker(llm)
                .check("q", "a", hits(), MODEL);
        assertEquals(GroundingResult.Verdict.UNKNOWN, out.verdict());
        assertEquals("", out.rationale());
    }

    @Test
    @DisplayName("returns UNKNOWN when the LLM does not reply")
    void check_returnsUnknownOnEmptyReply() {
        // StubLlmClient returns Optional.empty() for a prompt it has no
        // canned response for.
        StubLlmClient llm = new StubLlmClient();
        GroundingResult out = new DefaultGroundingChecker(llm)
                .check("q", "a", hits(), MODEL);
        assertEquals(GroundingResult.Verdict.UNKNOWN, out.verdict());
    }
}
