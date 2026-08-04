package io.github.rsweet.esdm2opencqrs.model;

import java.util.List;

public record ReadModel(
        String name,
        String domain,
        String boundedContext,
        String paradigm,
        Schema columns,
        List<Projection> projections) {

    /** One entry of a read model's {@code projections}: which event feeds the read model. */
    public record Projection(String aggregate, String event, String rule) {}

    public boolean projectsEvent(String event) {
        return projections.stream().anyMatch(p -> p.event().equals(event));
    }

    public List<String> projectedAggregates() {
        return projections.stream().map(Projection::aggregate).distinct().toList();
    }
}
