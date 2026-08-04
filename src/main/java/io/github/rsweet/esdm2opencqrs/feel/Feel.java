package io.github.rsweet.esdm2opencqrs.feel;

import java.util.ArrayList;
import java.util.List;

public final class Feel {

    private Feel() {}

    public static FeelNode parse(String source) {
        return Parser.parse(source);
    }

    /** Binds identifiers against a set of allowed fields; returns the binding errors (empty = valid). */
    public static List<String> validate(FeelNode ast, List<String> allowedFields) {
        List<String> errors = new ArrayList<>();
        bind(ast, allowedFields, errors);
        return errors;
    }

    private static void bind(FeelNode node, List<String> allowed, List<String> errors) {
        switch (node) {
            case FeelNode.Id id -> {
                if (!allowed.contains(id.name())) {
                    errors.add("unknown field \"" + id.name() + "\"");
                }
            }
            case FeelNode.Or or -> {
                bind(or.left(), allowed, errors);
                bind(or.right(), allowed, errors);
            }
            case FeelNode.And and -> {
                bind(and.left(), allowed, errors);
                bind(and.right(), allowed, errors);
            }
            case FeelNode.Binary binary -> {
                bind(binary.left(), allowed, errors);
                bind(binary.right(), allowed, errors);
            }
            case FeelNode.Not not -> bind(not.expression(), allowed, errors);
            case FeelNode.In in -> {
                bind(in.expression(), allowed, errors);
                in.list().forEach(item -> bind(item, allowed, errors));
            }
            default -> {}
        }
    }
}
