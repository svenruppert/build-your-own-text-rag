package com.svenruppert.flow.views.module04;

import com.svenruppert.flow.views.module03.Chunk;
import com.svenruppert.flow.views.module04.testutil.StubLlmClient;
import com.svenruppert.flow.views.module04.testutil.TestChunks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmJudgeRerankerTest {

    private static final String MODEL = "stub-model";

    @Test
    @DisplayName("reorders candidates by the parsed LLM score")
    void rerank_reordersCandidatesByParsedScore() {
        Chunk apple = TestChunks.of(0, "apples are red");
        Chunk dog = TestChunks.of(1, "dogs bark loudly");
        Chunk sky = TestChunks.of(2, "the sky is blue");

        List<RetrievalHit> candidates = List.of(
                new RetrievalHit(apple, 0.10, "vector"),
                new RetrievalHit(dog, 0.20, "vector"),
                new RetrievalHit(sky, 0.30, "vector"));

        // The stub matches "passage text" fragments in the prompt to
        // canned replies. Sky scores highest -> ends up first.
        StubLlmClient llm = new StubLlmClient()
                .withGenerate("apples are red", "2")
                .withGenerate("dogs bark loudly", "5")
                .withGenerate("the sky is blue", "9");

        List<RetrievalHit> out = new LlmJudgeReranker(llm, MODEL).rerank("q", candidates, 3);

        assertEquals(3, out.size());
        assertEquals(sky, out.get(0).chunk());
        assertEquals(dog, out.get(1).chunk());
        assertEquals(apple, out.get(2).chunk());
        for (RetrievalHit h : out) {
            assertEquals("llm-judge-reranked", h.sourceLabel());
        }
    }

    @Test
    @DisplayName("falls back to the original score when the reply is not a number")
    void rerank_fallsBackWhenResponseIsNotANumber() {
        Chunk a = TestChunks.of(0, "alpha text");
        Chunk b = TestChunks.of(1, "beta text");

        List<RetrievalHit> candidates = List.of(
                new RetrievalHit(a, 0.8, "vector"),   // high original score
                new RetrievalHit(b, 0.2, "vector"));

        // For 'a' the reply is not a number at all -> original score
        // kept (0.8, clamped to [0, 1]).
        // For 'b' the reply is a clean number; 7/10 = 0.7.
        StubLlmClient llm = new StubLlmClient()
                .withGenerate("alpha text", "I cannot answer that")
                .withGenerate("beta text", "7");

        List<RetrievalHit> out = new LlmJudgeReranker(llm, MODEL).rerank("q", candidates, 2);

        assertEquals(2, out.size());
        // On the normalised [0, 1] scale, a's retained 0.8 beats b's 0.7.
        assertEquals(a, out.get(0).chunk());
        assertEquals(0.8, out.get(0).score(), 1.0e-9);
        assertEquals(b, out.get(1).chunk());
        assertEquals(0.7, out.get(1).score(), 1.0e-9);
    }

    @Test
    @DisplayName("returns only the top k entries")
    void rerank_returnsTopKOnly() {
        Chunk a = TestChunks.of(0, "aaa");
        Chunk b = TestChunks.of(1, "bbb");
        Chunk c = TestChunks.of(2, "ccc");

        List<RetrievalHit> candidates = List.of(
                new RetrievalHit(a, 0.1, "vector"),
                new RetrievalHit(b, 0.1, "vector"),
                new RetrievalHit(c, 0.1, "vector"));

        StubLlmClient llm = new StubLlmClient()
                .withGenerate("aaa", "2")
                .withGenerate("bbb", "8")
                .withGenerate("ccc", "5");

        List<RetrievalHit> out = new LlmJudgeReranker(llm, MODEL).rerank("q", candidates, 2);

        assertEquals(2, out.size());
        assertEquals(b, out.get(0).chunk());
        assertEquals(c, out.get(1).chunk());
    }

    @Test
    @DisplayName("peels <think> tags off the reply before parsing the score")
    void rerank_stripsThinkingTagsBeforeScoring() {
        com.svenruppert.flow.views.module03.Chunk a = TestChunks.of(0, "text a");
        List<RetrievalHit> candidates = List.of(new RetrievalHit(a, 0.1, "vector"));

        StubLlmClient llm = new StubLlmClient().withGenerate(
                "text a",
                "<think>The query asks about X. The passage discusses X in depth.</think>\n9");

        List<RetrievalHit> out = new LlmJudgeReranker(llm, MODEL).rerank("q", candidates, 1);

        // Without stripping, the regex might pick up the first digit in
        // the thinking block (if any); with stripping, the 0-10 score
        // is 9 which normalises to 0.9.
        assertEquals(0.9, out.get(0).score(), 1.0e-9);
    }

    @Test
    @DisplayName("forwards the stripped thinking to the thinking observer")
    void rerank_notifiesThinkingObserverOncePerCandidate() {
        com.svenruppert.flow.views.module03.Chunk a = TestChunks.of(0, "text a");
        com.svenruppert.flow.views.module03.Chunk b = TestChunks.of(1, "text b");
        List<RetrievalHit> candidates = List.of(
                new RetrievalHit(a, 0.1, "vector"),
                new RetrievalHit(b, 0.1, "vector"));

        StubLlmClient llm = new StubLlmClient()
                .withGenerate("text a", "<think>analysing a</think> 3")
                .withGenerate("text b", "7");  // no thinking at all

        java.util.Map<RetrievalHit, String> observed = new java.util.HashMap<>();
        new LlmJudgeReranker(llm, MODEL,
                (candidate, thinking) -> observed.put(candidate, thinking))
                .rerank("q", candidates, 2);

        assertEquals(1, observed.size(), "only the candidate with <think> content fires the observer");
        assertEquals("analysing a",
                observed.values().iterator().next());
    }

    @Test
    @DisplayName("splitThinking returns (remainder, joined thinking) for multi-block replies")
    void splitThinking_handlesMultipleBlocks() {
        LlmJudgeReranker.Split split = LlmJudgeReranker.splitThinking(
                "<think>first pass</think> 4 <think>second pass</think>");
        assertEquals("first pass second pass", split.thinking());
        assertEquals("4", split.remainder());
    }

    @Test
    @DisplayName("thinking from the separate 'thinking' field reaches the observer too")
    void rerank_capturesSeparateThinkingField() {
        com.svenruppert.flow.views.module03.Chunk a = TestChunks.of(0, "text a");
        List<RetrievalHit> candidates = List.of(new RetrievalHit(a, 0.1, "vector"));

        // Inline LlmClient that surfaces thinking on the separate
        // field, the way newer Ollama does when the request carries
        // think=true. Every other method is a no-op; LlmJudgeReranker
        // only calls generateWithThinking.
        com.svenruppert.flow.views.module01.LlmClient llm =
                new com.svenruppert.flow.views.module01.LlmClient() {
                    @Override
                    public java.util.Optional<java.util.List<String>> listModels() {
                        return java.util.Optional.empty();
                    }

                    @Override
                    public java.util.Optional<float[]> embed(String text, String model) {
                        return java.util.Optional.empty();
                    }

                    @Override
                    public java.util.Optional<String> generate(String prompt, String model) {
                        return java.util.Optional.empty();
                    }

                    @Override
                    public java.util.Optional<String> generate(String prompt, String model,
                                                               java.util.List<String> context) {
                        return java.util.Optional.empty();
                    }

                    @Override
                    public boolean isAvailable() {
                        return true;
                    }

                    @Override
                    public java.util.Optional<com.svenruppert.flow.views.module01.ThinkingReply>
                            generateWithThinking(String prompt, String model) {
                        return java.util.Optional.of(
                                new com.svenruppert.flow.views.module01.ThinkingReply(
                                        "8",
                                        "I looked at the passage carefully."));
                    }
                };

        java.util.Map<RetrievalHit, String> observed = new java.util.HashMap<>();
        List<RetrievalHit> out = new LlmJudgeReranker(llm, MODEL,
                (candidate, thinking) -> observed.put(candidate, thinking))
                .rerank("q", candidates, 1);

        assertEquals(1, observed.size());
        assertEquals("I looked at the passage carefully.",
                observed.values().iterator().next());
        assertEquals(0.8, out.get(0).score(), 1.0e-9);
    }
}
