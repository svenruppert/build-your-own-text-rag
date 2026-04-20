package com.svenruppert.flow.views.module05;

import java.util.Objects;

/**
 * A single event on the streaming completion channel.
 *
 * <p>Newer Ollama builds separate a reasoning model's internal
 * "thinking" tokens from the user-facing answer tokens: each
 * streaming frame carries both a {@code response} and a
 * {@code thinking} field, either of which may be empty. This sealed
 * type surfaces that split all the way up to the {@link Generator}
 * and the UI:
 *
 * <ul>
 *   <li>{@link Token} -- one (or a small batch of) user-facing
 *       answer token(s);</li>
 *   <li>{@link Thinking} -- one (or a small batch of) reasoning
 *       token(s) from a thinking model.</li>
 * </ul>
 *
 * <p>Models that do not emit thinking simply never yield a
 * {@link Thinking} event; the rest of the pipeline is unchanged.
 */
public sealed interface StreamEvent {

    /** Answer-facing token. */
    record Token(String text) implements StreamEvent {
        public Token {
            Objects.requireNonNull(text, "text");
        }
    }

    /** Internal reasoning token from a thinking model. */
    record Thinking(String text) implements StreamEvent {
        public Thinking {
            Objects.requireNonNull(text, "text");
        }
    }
}
