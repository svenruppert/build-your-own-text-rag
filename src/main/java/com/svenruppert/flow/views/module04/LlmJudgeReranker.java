package com.svenruppert.flow.views.module04;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.views.module01.LlmClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
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
 * available on any Ollama install without requiring the dedicated
 * {@code /api/rerank} endpoint. {@link BgeReranker} covers that path.
 */
public final class LlmJudgeReranker implements Reranker, HasLogger {

    /** Default judge model; any instruction-tuned model will do. */
    public static final String DEFAULT_MODEL = "llama3.2";

    /**
     * Matches the first integer or decimal number in the LLM reply.
     * Tolerates preambles like "Score: " or "I would say 7." since
     * small models are talkative.
     */
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    private static final String PROMPT_TEMPLATE = """
            On a scale of 0 to 10, how relevant is the following passage to the query?
            Reply with just a number.
            Query: %s
            Passage: %s
            """;

    private final LlmClient llmClient;
    private final String model;

    public LlmJudgeReranker(LlmClient llmClient) {
        this(llmClient, DEFAULT_MODEL);
    }

    public LlmJudgeReranker(LlmClient llmClient, String model) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        this.model = Objects.requireNonNull(model, "model");
    }

    @Override
    public List<RetrievalHit> rerank(String query, List<RetrievalHit> candidates, int k) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(candidates, "candidates");
        if (k <= 0 || candidates.isEmpty()) return List.of();

        List<RetrievalHit> scored = new ArrayList<>(candidates.size());
        for (RetrievalHit candidate : candidates) {
            String prompt = String.format(PROMPT_TEMPLATE, query, candidate.chunk().text());
            Optional<String> reply = llmClient.generate(prompt, model);
            double score = resolveScore(reply, candidate);
            scored.add(new RetrievalHit(candidate.chunk(), score, "llm-judge-reranked"));
        }
        scored.sort(Comparator.comparingDouble(RetrievalHit::score).reversed());
        return scored.stream().limit(k).toList();
    }

    /**
     * Folds an LLM reply into a score. If the model returned nothing, or
     * returned prose without a number, the candidate <em>keeps its
     * original score</em> -- sinking a useful candidate to zero because
     * the judge got chatty would distort the ranking more than the
     * missing signal already does. A warning is logged in either case.
     */
    private double resolveScore(Optional<String> reply, RetrievalHit candidate) {
        if (reply.isEmpty()) {
            logger().warn("LLM reply missing for chunk at offset {}; "
                    + "keeping original score", candidate.chunk().startOffset());
            return candidate.score();
        }
        OptionalDouble parsed = parseScore(reply.get());
        if (parsed.isEmpty()) {
            logger().warn("Cannot parse a number out of reply: '{}' -- "
                    + "keeping original score",
                    reply.get().length() > 80
                            ? reply.get().substring(0, 80) + "..."
                            : reply.get());
            return candidate.score();
        }
        return parsed.getAsDouble();
    }

    /**
     * Extracts the first numeric substring from the reply. Empty result
     * signals "no number found" to the caller so it can decide how to
     * fall back.
     */
    private OptionalDouble parseScore(String reply) {
        Matcher matcher = NUMBER_PATTERN.matcher(reply);
        if (!matcher.find()) return OptionalDouble.empty();
        try {
            return OptionalDouble.of(Double.parseDouble(matcher.group()));
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }
}
