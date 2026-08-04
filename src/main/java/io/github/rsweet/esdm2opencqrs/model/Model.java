package io.github.rsweet.esdm2opencqrs.model;

import java.util.List;

/**
 * The resolved, framework-agnostic ESDM model: a domain with its bounded contexts and the
 * aggregates/events/commands/read-models/queries inside them, with all cross-references already
 * wired. Adapters consume this; they never touch raw YAML.
 */
public record Model(
        String domain, List<BoundedContext> boundedContexts, List<Feature> features, List<Policy> policies) {

    public Aggregate aggregate(String boundedContext, String name) {
        return boundedContexts.stream()
                .filter(context -> context.name().equals(boundedContext))
                .flatMap(context -> context.aggregates().stream())
                .filter(aggregate -> aggregate.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    public List<Aggregate> aggregates() {
        return boundedContexts.stream()
                .flatMap(context -> context.aggregates().stream())
                .toList();
    }

    public BoundedContext boundedContext(String name) {
        return boundedContexts.stream()
                .filter(context -> context.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    public List<Feature> featuresFor(String boundedContext, String aggregate) {
        return features.stream()
                .filter(f -> f.boundedContext().equals(boundedContext) && f.aggregate().equals(aggregate))
                .toList();
    }
}
