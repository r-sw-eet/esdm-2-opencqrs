package io.github.rsweet.esdm2opencqrs.model;

public record Query(String name, String domain, String boundedContext, String readModel, Schema parameters) {

    /** A query with parameters reads a single row; without, it lists the read model. */
    public boolean isGet() {
        return !parameters.isEmpty();
    }
}
