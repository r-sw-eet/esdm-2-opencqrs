package io.github.rsweet.esdm2opencqrs.model;

import java.util.List;

public record Command(
        String name,
        String domain,
        String boundedContext,
        String aggregate,
        Schema data,
        List<String> publishes,
        Lifecycle lifecycle) {

    public String primaryEvent() {
        return publishes.isEmpty() ? null : publishes.get(0);
    }
}
