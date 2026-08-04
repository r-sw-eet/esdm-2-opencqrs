package io.github.rsweet.esdm2opencqrs.model;

import java.util.ArrayList;
import java.util.List;

public final class BoundedContext {

    private final String name;
    private final String domain;
    private final List<Aggregate> aggregates = new ArrayList<>();
    private final List<ReadModel> readModels = new ArrayList<>();
    private final List<Query> queries = new ArrayList<>();

    public BoundedContext(String name, String domain) {
        this.name = name;
        this.domain = domain;
    }

    public String name() {
        return name;
    }

    public String domain() {
        return domain;
    }

    public List<Aggregate> aggregates() {
        return aggregates;
    }

    public List<ReadModel> readModels() {
        return readModels;
    }

    public List<Query> queries() {
        return queries;
    }

    public ReadModel readModel(String readModelName) {
        return readModels.stream()
                .filter(r -> r.name().equals(readModelName))
                .findFirst()
                .orElse(null);
    }
}
