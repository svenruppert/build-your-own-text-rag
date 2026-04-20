package com.svenruppert.flow.views.module05;

import com.svenruppert.flow.views.module04.RetrievalHit;
import com.svenruppert.flow.views.module04.testutil.TestChunks;
import com.svenruppert.flow.views.module05.testutil.StubStreamingLlmApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultGeneratorTest {

    private static final String MODEL = "stub-model";

    private static List<RetrievalHit> threeHits() {
        return List.of(
                new RetrievalHit(TestChunks.of(0, "chunk zero"), 0.9, "vector"),
                new RetrievalHit(TestChunks.of(1, "chunk one"), 0.8, "vector"),
                new RetrievalHit(TestChunks.of(2, "chunk two"), 0.7, "vector"));
    }

    @Test
    @DisplayName("forwards every token to the sink in the emitted order")
    void generate_forwardsEveryTokenToTheSink() {
        StubStreamingLlmApi api = new StubStreamingLlmApi()
                .emitting("The ", "answer ", "is ", "42.");
        Generator gen = new DefaultGenerator(api, new SimpleGroundedPromptTemplate());

        List<String> observed = new ArrayList<>();
        gen.generate("q", threeHits(), MODEL, observed::add);

        assertEquals(List.of("The ", "answer ", "is ", "42."), observed);
    }

    @Test
    @DisplayName("accumulates tokens into the final text")
    void generate_accumulatesTokensIntoFinalText() {
        StubStreamingLlmApi api = new StubStreamingLlmApi()
                .emitting("Hello ", "world", "!");
        Generator gen = new DefaultGenerator(api, new SimpleGroundedPromptTemplate());

        GeneratedAnswer answer = gen.generate("q", threeHits(), MODEL, null);
        assertEquals("Hello world!", answer.text());
    }

    @Test
    @DisplayName("extracts cited chunk indices from the finished text")
    void generate_extractsCitedChunkIndicesFromText() {
        StubStreamingLlmApi api = new StubStreamingLlmApi()
                .emitting("As per [Chunk 1] and [Chunk 3], the answer is known.");
        Generator gen = new DefaultGenerator(api, new SimpleGroundedPromptTemplate());

        GeneratedAnswer answer = gen.generate("q", threeHits(), MODEL, null);
        assertEquals(List.of(0, 2), answer.citedChunkIndices());
    }

    @Test
    @DisplayName("detects refusal phrase in the first 200 characters")
    void generate_detectsRefusalPhrase() {
        StubStreamingLlmApi api = new StubStreamingLlmApi()
                .emitting("I don't know.");
        Generator gen = new DefaultGenerator(api, new SimpleGroundedPromptTemplate());

        GeneratedAnswer answer = gen.generate("q", threeHits(), MODEL, null);
        assertTrue(answer.refusalDetected());
    }

    @Test
    @DisplayName("returns an empty answer when the stream fails")
    void generate_returnsEmptyAnswerOnStreamingFailure() {
        StubStreamingLlmApi api = new StubStreamingLlmApi().failing();
        Generator gen = new DefaultGenerator(api, new SimpleGroundedPromptTemplate());

        GeneratedAnswer answer = gen.generate("q", threeHits(), MODEL, null);
        assertEquals("", answer.text());
        assertTrue(answer.citedChunkIndices().isEmpty());
        assertTrue(!answer.refusalDetected());
    }

    @Test
    @DisplayName("splits Thinking and Token events into separate sinks")
    void generate_routesThinkingAndTokenEventsSeparately() {
        StubStreamingLlmApi api = new StubStreamingLlmApi().emittingEvents(
                new StreamEvent.Thinking("Let me think..."),
                new StreamEvent.Token("Paris "),
                new StreamEvent.Thinking(" checking the chunk."),
                new StreamEvent.Token("is the capital of France."));
        Generator gen = new DefaultGenerator(api, new SimpleGroundedPromptTemplate());

        List<String> tokens = new ArrayList<>();
        List<String> thinking = new ArrayList<>();
        GeneratedAnswer answer = gen.generate(
                "q", threeHits(), MODEL, tokens::add, thinking::add);

        assertEquals(List.of("Paris ", "is the capital of France."), tokens);
        assertEquals(List.of("Let me think...", " checking the chunk."), thinking);
        assertEquals("Paris is the capital of France.", answer.text());
        assertEquals("Let me think... checking the chunk.", answer.thinking());
    }

    @Test
    @DisplayName("the thinking field stays empty when the stream emits no Thinking events")
    void generate_thinkingEmptyWhenNoThinkingEvents() {
        StubStreamingLlmApi api = new StubStreamingLlmApi().emitting("Hi there.");
        Generator gen = new DefaultGenerator(api, new SimpleGroundedPromptTemplate());

        GeneratedAnswer answer = gen.generate("q", threeHits(), MODEL, s -> { });
        assertEquals("", answer.thinking());
    }
}
