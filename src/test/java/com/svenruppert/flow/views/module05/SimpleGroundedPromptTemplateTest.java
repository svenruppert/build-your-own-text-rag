package com.svenruppert.flow.views.module05;

import com.svenruppert.flow.views.module04.RetrievalHit;
import com.svenruppert.flow.views.module04.testutil.TestChunks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleGroundedPromptTemplateTest {

    private final PromptTemplate template = new SimpleGroundedPromptTemplate();

    private static List<RetrievalHit> hits() {
        return List.of(
                new RetrievalHit(TestChunks.of(0, "alpha text"), 0.9, "vector"),
                new RetrievalHit(TestChunks.of(1, "beta content"), 0.8, "vector"),
                new RetrievalHit(TestChunks.of(2, "gamma words"), 0.7, "vector"));
    }

    @Test
    @DisplayName("prompt contains the query verbatim")
    void buildPrompt_containsQueryVerbatim() {
        String prompt = template.buildPrompt("why is the sky blue?", hits());
        assertTrue(prompt.contains("why is the sky blue?"));
    }

    @Test
    @DisplayName("prompt contains every chunk text verbatim")
    void buildPrompt_containsEveryChunkTextVerbatim() {
        String prompt = template.buildPrompt("q", hits());
        assertTrue(prompt.contains("alpha text"));
        assertTrue(prompt.contains("beta content"));
        assertTrue(prompt.contains("gamma words"));
    }

    @Test
    @DisplayName("chunks are numbered starting at 1")
    void buildPrompt_numbersChunksStartingAtOne() {
        String prompt = template.buildPrompt("q", hits());
        assertTrue(prompt.contains("[Chunk 1]"));
        assertTrue(prompt.contains("[Chunk 2]"));
        assertTrue(prompt.contains("[Chunk 3]"));
    }

    @Test
    @DisplayName("prompt instructs the model to cite with the [Chunk N] convention")
    void buildPrompt_instructsModelToCiteWithChunkNConvention() {
        String prompt = template.buildPrompt("q", hits());
        assertTrue(prompt.toLowerCase().contains("cite"),
                "prompt must spell out the citation instruction");
        assertTrue(prompt.contains("[Chunk 2]"),
                "prompt must show the citation format");
    }

    @Test
    @DisplayName("prompt instructs the model to refuse if the answer is not in the chunks")
    void buildPrompt_instructsModelToRefuseIfAnswerNotPresent() {
        String prompt = template.buildPrompt("q", hits());
        assertTrue(prompt.contains("I don't know."),
                "prompt must tell the model to reply with 'I don't know.'");
    }

    @Test
    @DisplayName("the query appears after the chunks (context first, question last)")
    void buildPrompt_placesQueryAfterChunks() {
        String prompt = template.buildPrompt("the question", hits());
        int questionIndex = prompt.indexOf("the question");
        int firstChunkIndex = prompt.indexOf("alpha text");
        int lastChunkIndex = prompt.indexOf("gamma words");
        assertTrue(firstChunkIndex < questionIndex);
        assertTrue(lastChunkIndex < questionIndex);
    }
}
