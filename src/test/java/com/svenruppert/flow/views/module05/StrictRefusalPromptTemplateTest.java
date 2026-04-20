package com.svenruppert.flow.views.module05;

import com.svenruppert.flow.views.module04.RetrievalHit;
import com.svenruppert.flow.views.module04.testutil.TestChunks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StrictRefusalPromptTemplateTest {

    private final PromptTemplate template = new StrictRefusalPromptTemplate();

    @Test
    @DisplayName("carries an aggressive refusal instruction ('ONLY' wording)")
    void buildPrompt_containsAggressiveRefusalInstruction() {
        String prompt = template.buildPrompt("q",
                List.of(new RetrievalHit(TestChunks.of(0, "some text"), 0.5, "vector")));
        assertTrue(prompt.contains("Answer ONLY"),
                "strict template must carry the ONLY-from-chunks wording");
        assertTrue(prompt.contains("I don't know."),
                "strict template must still instruct the refusal reply");
    }

    @Test
    @DisplayName("still carries all chunks and the query")
    void buildPrompt_stillCarriesAllChunksAndQuery() {
        List<RetrievalHit> hits = List.of(
                new RetrievalHit(TestChunks.of(0, "first"), 0.9, "vector"),
                new RetrievalHit(TestChunks.of(1, "second"), 0.8, "vector"));
        String prompt = template.buildPrompt("my query", hits);
        assertTrue(prompt.contains("first"));
        assertTrue(prompt.contains("second"));
        assertTrue(prompt.contains("[Chunk 1]"));
        assertTrue(prompt.contains("[Chunk 2]"));
        assertTrue(prompt.contains("my query"));
    }
}
