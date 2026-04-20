package com.svenruppert.flow.views.module05.testutil;

import com.svenruppert.flow.views.module05.StreamEvent;
import com.svenruppert.flow.views.module05.StreamingLlmApi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Test double for {@link StreamingLlmApi}: emits a canned sequence
 * of {@link StreamEvent}s on each {@code streamEvents} call. Lets
 * {@link com.svenruppert.flow.views.module05.DefaultGenerator}-level
 * tests exercise both the token and the thinking callback paths
 * without any network.
 *
 * <p>Two builders:
 * <ul>
 *   <li>{@link #emitting} -- wraps plain strings as
 *       {@link StreamEvent.Token} events (backward-compatible with
 *       the pre-thinking stub API);</li>
 *   <li>{@link #emittingEvents} -- takes pre-built {@link StreamEvent}s
 *       so a test can interleave thinking and answer tokens.</li>
 * </ul>
 */
public final class StubStreamingLlmApi implements StreamingLlmApi {

    private Supplier<Stream<StreamEvent>> eventsSupplier = Stream::empty;

    public StubStreamingLlmApi emitting(String... tokens) {
        List<StreamEvent> snapshot = new ArrayList<>(tokens.length);
        for (String token : tokens) snapshot.add(new StreamEvent.Token(token));
        this.eventsSupplier = snapshot::stream;
        return this;
    }

    public StubStreamingLlmApi emittingEvents(StreamEvent... events) {
        List<StreamEvent> snapshot = List.of(events);
        this.eventsSupplier = snapshot::stream;
        return this;
    }

    public StubStreamingLlmApi failing() {
        this.eventsSupplier = () -> {
            throw new IllegalStateException("stub streaming failure");
        };
        return this;
    }

    @Override
    public Stream<StreamEvent> streamEvents(String prompt, String model) {
        return eventsSupplier.get();
    }
}
