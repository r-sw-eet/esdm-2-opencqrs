package io.github.rsweet.esdm2opencqrs.model;

/**
 * An ESDM {@code policy}: a stateless reaction that emits a command when an event occurs - the
 * cross-aggregate, often cross-context glue of an event-driven system.
 *
 * <p>{@code mapping} is the raw {@code esdm-extensions.io/mapping} annotation (proposal 0005): a
 * FEEL context literal saying what the emitted command carries. Empty means the default
 * convention applies, so today's models emit exactly what they emitted before.
 */
public record Policy(
        String name,
        String domain,
        String handleContext,
        String handleAggregate,
        String handleEvent,
        String emitContext,
        String emitAggregate,
        String emitCommand,
        String mapping) {}
