package com.svenruppert.flow.views.module05;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.views.module01.LlmClient;
import com.svenruppert.flow.views.module04.RetrievalHit;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Non-streaming {@link GroundingChecker} that asks a generative
 * model to classify a finished answer against the chunks it was
 * supposed to be grounded in.
 *
 * <p>Prompt shape:
 * <pre>
 * Decide whether ANSWER is grounded in CHUNKS for QUERY.
 * Reply in this exact format:
 *   VERDICT: GROUNDED|PARTIAL|NOT_GROUNDED
 *   RATIONALE: &lt;one sentence&gt;
 * </pre>
 *
 * <p>Replies are parsed with two small regexes. Malformed, empty or
 * missing replies surface as {@link GroundingResult.Verdict#UNKNOWN}
 * with an empty rationale -- the checker never throws.
 */
public final class DefaultGroundingChecker implements GroundingChecker, HasLogger {

    private static final Pattern VERDICT_PATTERN =
            Pattern.compile("VERDICT:\\s*(GROUNDED|PARTIAL|NOT_GROUNDED)",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern RATIONALE_PATTERN =
            Pattern.compile("RATIONALE:\\s*(.+?)(?:\\n|$)",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final LlmClient llmClient;

    public DefaultGroundingChecker(LlmClient llmClient) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
    }

    @Override
    public GroundingResult check(String query, String answer,
                                 List<RetrievalHit> hits, String model) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(answer, "answer");
        Objects.requireNonNull(hits, "hits");
        Objects.requireNonNull(model, "model");

        String prompt = buildPrompt(query, answer, hits);
        Optional<String> reply = llmClient.generate(prompt, model);
        if (reply.isEmpty() || reply.get().isBlank()) {
            logger().warn("grounding check returned no reply");
            return GroundingResult.unknown();
        }
        return parseReply(reply.get());
    }

    private String buildPrompt(String query, String answer, List<RetrievalHit> hits) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                You are a fact-checking judge.
                Decide whether ANSWER is grounded in the provided CHUNKS for the given QUERY.
                Reply in this exact format, one pair per line:
                VERDICT: GROUNDED|PARTIAL|NOT_GROUNDED
                RATIONALE: <one short sentence>
                """);
        sb.append("\n=== CHUNKS ===\n");
        int number = 1;
        for (RetrievalHit hit : hits) {
            sb.append("[Chunk ").append(number++).append("]\n");
            sb.append(hit.chunk().text()).append("\n\n");
        }
        sb.append("=== QUERY ===\n").append(query).append("\n\n");
        sb.append("=== ANSWER ===\n").append(answer).append('\n');
        return sb.toString();
    }

    private GroundingResult parseReply(String reply) {
        Matcher verdictMatcher = VERDICT_PATTERN.matcher(reply);
        if (!verdictMatcher.find()) {
            logger().warn("grounding check reply carries no VERDICT line: '{}'",
                    reply.length() > 120 ? reply.substring(0, 120) + "..." : reply);
            return GroundingResult.unknown();
        }
        GroundingResult.Verdict verdict;
        try {
            verdict = GroundingResult.Verdict.valueOf(
                    verdictMatcher.group(1).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return GroundingResult.unknown();
        }
        Matcher rationaleMatcher = RATIONALE_PATTERN.matcher(reply);
        String rationale = rationaleMatcher.find() ? rationaleMatcher.group(1).trim() : "";
        return new GroundingResult(verdict, rationale);
    }
}
