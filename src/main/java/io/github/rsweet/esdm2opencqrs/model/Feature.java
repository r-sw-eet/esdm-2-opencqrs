package io.github.rsweet.esdm2opencqrs.model;

import java.util.List;
import java.util.Map;

/**
 * A GWT {@code feature} document (aggregate variant): a set of scenarios about one aggregate.
 * Extension documents never enter the core cross-reference graph; they are resolved against it by
 * name at emit time.
 */
public record Feature(String name, String domain, String boundedContext, String aggregate, List<Scenario> scenarios) {

    /** One reference to an event with concrete data, used in scenario given/then. */
    public record EventExample(String event, Map<String, Object> data) {}

    /**
     * Replay {@code given} events, apply the {@code when} command, expect either {@code then} events
     * or a rejection.
     */
    public record Scenario(
            String name,
            List<EventExample> given,
            String commandName,
            Map<String, Object> commandData,
            List<EventExample> thenEvents,
            String rejectionReason) {

        public boolean isRejection() {
            return rejectionReason != null;
        }
    }
}
