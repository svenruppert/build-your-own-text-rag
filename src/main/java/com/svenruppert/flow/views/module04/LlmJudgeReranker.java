package com.svenruppert.flow.views.module04;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.WorkshopDefaults;
import com.svenruppert.flow.views.module01.LlmClient;
import com.svenruppert.flow.views.module01.ThinkingReply;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM-as-judge reranker: coaxes a general instruction-tuned model into
 * producing a relevance score for each candidate via a short
 * natural-language prompt, then sorts by that score.
 *
 * <p><strong>Not</strong> a true cross-encoder. A proper cross-encoder
 * is a small model trained specifically to emit a single relevance
 * score from a (query, passage) pair. Here we are piggybacking on a
 * general-purpose LLM, which is slower and noisier -- but pleasantly
 * available on any Ollama install. The cross-encoder route is
 * intentionally absent from this module because Ollama exposes no
 * dedicated reranking endpoint; production code typically reaches it
 * via ONNX Runtime, outside the workshop's no-native-code rule.
 *
 * <h2>Thinking models</h2>
 * Reasoning models (deepseek-r1, qwen3-*thinking*, ...) tend to wrap
 * their chain of thought in {@code <think>...</think>} tags before the
 * actual answer. The reranker peels those tags off before looking for
 * the score number, so they do not poison the parse. The extracted
 * thinking can be observed through the optional
 * {@link #LlmJudgeReranker(LlmClient, String, BiConsumer) thinking
 * observer} constructor -- Module 04's Retrieval Lab uses it to show
 * the judge's reasoning per candidate.
 */
public final class LlmJudgeReranker implements Reranker, HasLogger {

    /** Default judge model; any instruction-tuned model will do. */
    public static final String DEFAULT_MODEL = WorkshopDefaults.DEFAULT_GENERATION_MODEL;

    /**
     * Matches the first integer or decimal number in the LLM reply.
     * Tolerates preambles like "Score: " or "I would say 7." since
     * small models are talkative.
     */
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    /**
     * Captures the reasoning block of a thinking model, if present.
     * {@code [\\s\\S]*?} is the non-greedy "any character including
     * newline" idiom that works without the {@code DOTALL} flag.
     */
    private static final Pattern THINK_TAG = Pattern.compile("<think>([\\s\\S]*?)</think>");

    private static final String PROMPT_TEMPLATE = """
            On a scale of 0 to 10, how relevant is the following passage to the query?
            Reply with just a number.
            Query: %s
            Passage: %s
            """;

    private final LlmClient llmClient;
    private final String model;
    private final BiConsumer<RetrievalHit, String> thinkingObserver;
    private final ProgressObserver progressObserver;

    /**
     * Per-candidate progress hook. Fires twice per candidate while
     * {@link #rerank(String, List, int)} runs: once with
     * {@link Phase#STARTED} before the LLM request and once with
     * {@link Phase#FINISHED} right after the reply has been parsed into
     * a score. The view layer uses it to drive a progress bar and keep
     * participants informed while long reasoning models chew through a
     * batch.
     */
    @FunctionalInterface
    public interface ProgressObserver {
        void onProgress(Phase phase, int index, int total, RetrievalHit candidate);
    }

    /** Phases reported through {@link ProgressObserver}. */
    public enum Phase { STARTED, FINISHED }

    public LlmJudgeReranker(LlmClient llmClient) {
        this(llmClient, DEFAULT_MODEL, (hit, thinking) -> { }, null);
    }

    public LlmJudgeReranker(LlmClient llmClient, String model) {
        this(llmClient, model, (hit, thinking) -> { }, null);
    }

    public LlmJudgeReranker(LlmClient llmClient, String model,
                            BiConsumer<RetrievalHit, String> thinkingObserver) {
        this(llmClient, model, thinkingObserver, null);
    }

    /**
     * @param thinkingObserver invoked once per candidate with the
     *                         model's chain of thought whenever the
     *                         reply actually carried
     *                         {@code <think>...</think>} content.
     *                         Passing {@code null} falls back to a
     *                         no-op observer.
     * @param progressObserver per-candidate progress hook (see
     *                         {@link ProgressObserver}). Passing
     *                         {@code null} falls back to a no-op.
     */
    public LlmJudgeReranker(LlmClient llmClient, String model,
                            BiConsumer<RetrievalHit, String> thinkingObserver,
                            ProgressObserver progressObserver) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        this.model = Objects.requireNonNull(model, "model");
        this.thinkingObserver = (thinkingObserver == null)
                ? (hit, thinking) -> { }
                : thinkingObserver;
        this.progressObserver = (progressObserver == null)
                ? (phase, index, total, hit) -> { }
                : progressObserver;
    }

    @Override
    public List<RetrievalHit> rerank(String query, List<RetrievalHit> candidates, int k) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(candidates, "candidates");
        if (k <= 0 || candidates.isEmpty()) return List.of();

        List<RetrievalHit> scored = new ArrayList<>(candidates.size());
        int total = candidates.size();
        for (int i = 0; i < total; i++) {
            RetrievalHit candidate = candidates.get(i);
            progressObserver.onProgress(Phase.STARTED, i, total, candidate);
            String prompt = String.format(PROMPT_TEMPLATE, query, candidate.chunk().text());
            // Ask the thinking-aware variant so reasoning models
            // surface their chain of thought on the dedicated field.
            // The default LlmClient implementation falls back to plain
            // generate() for non-Ollama backends, so this call is safe
            // even on clients that do not speak /api/generate's
            // think flag.
            Optional<ThinkingReply> reply = llmClient.generateWithThinking(prompt, model);
            double score = resolveScore(reply, candidate);
            RetrievalHit rescored = new RetrievalHit(candidate.chunk(), score, "llm-judge-reranked");
            scored.add(rescored);
            progressObserver.onProgress(Phase.FINISHED, i, total, rescored);
        }
        scored.sort(Comparator.comparingDouble(RetrievalHit::score).reversed());
        return scored.stream().limit(k).toList();
    }

    /**
     * Folds an LLM reply into a score in {@code [0, 1]}.
     *
     * <p>The prompt asks the judge to rate relevance on a
     * <em>0-10</em> scale because small LLMs emit integer ratings on
     * that scale more reliably than decimals in {@code [0, 1]}. We
     * then divide by 10 and clamp so the reranker output sits in the
     * same {@code [0, 1]} band a cross-encoder reranker would
     * naturally produce.
     *
     * <p>On parse failure or missing reply we fall back to the
     * candidate's <em>original</em> score -- clamped to {@code [0, 1]}
     * as well -- so a BM25 raw score (unbounded) cannot smuggle a
     * double-digit value into the reranked list. The didactic trade-off
     * is that a kept-original score only makes sense when the
     * first-stage retriever's native scale overlaps {@code [0, 1]}
     * (vector cosine, weighted-hybrid); for BM25 or RRF the fallback
     * saturates at 1.0.
     *
     * <p>Along the way, any {@code <think>...</think>} block is peeled
     * off so the number regex runs on the remainder only; the peeled
     * thinking is forwarded to the {@link #thinkingObserver}.
     */
    private double resolveScore(Optional<ThinkingReply> reply, RetrievalHit candidate) {
        if (reply.isEmpty()) {
            logger().warn("LLM reply missing for chunk at offset {}; "
                    + "keeping original score", candidate.chunk().startOffset());
            return clamp01(candidate.score());
        }
        ThinkingReply raw = reply.get();

        // Two thinking sources, handled together:
        //   (1) inline <think>...</think> blocks inside the response
        //       text (older Ollama, or models that emit tags natively);
        //   (2) the separate `thinking` field in the JSON reply (newer
        //       Ollama with think=true).
        // Both feed the same observer; (1) is also stripped from the
        // response so the score regex only sees the answer.
        Split split = splitThinking(raw.response());
        String combinedThinking = combineThinking(raw.thinking(), split.thinking());
        if (!combinedThinking.isEmpty()) {
            thinkingObserver.accept(candidate, combinedThinking);
        }

        OptionalDouble parsed = parseScore(split.remainder());
        if (parsed.isEmpty()) {
            logger().warn("Cannot parse a number out of reply: '{}' -- "
                    + "keeping original score",
                    truncate(split.remainder()));
            return clamp01(candidate.score());
        }
        return clamp01(parsed.getAsDouble() / 10.0);
    }

    private static String combineThinking(String separate, String inline) {
        String s = separate == null ? "" : separate.trim();
        String i = inline == null ? "" : inline.trim();
        if (s.isEmpty()) return i;
        if (i.isEmpty()) return s;
        return s + "\n\n" + i;
    }

    /** Clamp to {@code [0, 1]} -- keeps out-of-range inputs well-behaved. */
    private static double clamp01(double v) {
        if (Double.isNaN(v)) return 0.0;
        return Math.max(0.0, Math.min(1.0, v));
    }

    /**
     * Extracts and removes all {@code <think>...</think>} blocks from
     * the reply. Multiple blocks are concatenated with a space so the
     * observer sees the full reasoning in reading order.
     */
    static Split splitThinking(String reply) {
        Matcher matcher = THINK_TAG.matcher(reply);
        if (!matcher.find()) return new Split(reply, "");
        StringBuilder thinking = new StringBuilder();
        StringBuilder remainder = new StringBuilder();
        int cursor = 0;
        matcher.reset();
        while (matcher.find()) {
            remainder.append(reply, cursor, matcher.start());
            if (!thinking.isEmpty()) thinking.append(' ');
            thinking.append(matcher.group(1).trim());
            cursor = matcher.end();
        }
        remainder.append(reply, cursor, reply.length());
        return new Split(remainder.toString().trim(), thinking.toString());
    }

    private OptionalDouble parseScore(String text) {
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        if (!matcher.find()) return OptionalDouble.empty();
        try {
            return OptionalDouble.of(Double.parseDouble(matcher.group()));
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }

    private static String truncate(String s) {
        return s.length() > 80 ? s.substring(0, 80) + "..." : s;
    }

    /** Reply split into (non-think remainder, concatenated thinking). */
    record Split(String remainder, String thinking) {
    }
}
