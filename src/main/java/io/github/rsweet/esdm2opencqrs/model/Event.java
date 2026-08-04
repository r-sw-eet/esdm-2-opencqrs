package io.github.rsweet.esdm2opencqrs.model;

public record Event(
        String name,
        String domain,
        String boundedContext,
        String aggregate,
        Schema data,
        Lifecycle lifecycle,
        String type) {}
