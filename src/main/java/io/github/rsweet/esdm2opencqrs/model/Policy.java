package io.github.rsweet.esdm2opencqrs.model;

/**
 * An ESDM {@code policy}: a stateless reaction that emits a command when an event occurs - the
 * cross-aggregate, often cross-context glue of an event-driven system.
 */
public record Policy(
        String name,
        String domain,
        String handleContext,
        String handleAggregate,
        String handleEvent,
        String emitContext,
        String emitAggregate,
        String emitCommand) {}
