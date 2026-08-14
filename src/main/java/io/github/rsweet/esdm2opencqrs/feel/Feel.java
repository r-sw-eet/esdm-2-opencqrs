package io.github.rsweet.esdm2opencqrs.feel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Feel {

    private Feel() {}

    public static FeelNode parse(String source) {
        return Parser.parse(source);
    }

    /** Binds identifiers against a set of allowed fields; returns the binding errors (empty = valid). */
    public static List<String> validate(FeelNode ast, List<String> allowedFields) {
        return validate(ast, allowedFields, Map.of());
    }

    /**
     * As above, plus the arithmetic gates of the 2026-08-14 amendment: an operand declared
     * {@code string} or {@code boolean} is not arithmetic, and a literal zero divisor never is.
     * Types are the model's own {@code jsonType} per field; an absent entry skips the type check.
     */
    public static List<String> validate(FeelNode ast, List<String> allowedFields, Map<String, String> fieldTypes) {
        List<String> errors = new ArrayList<>();
        bind(ast, allowedFields, errors);
        arithmetic(ast, fieldTypes, errors);
        return errors;
    }

    private static final List<String> ARITHMETIC = List.of("+", "-", "*", "/");

    private static final Map<String, Integer> ARITY = Map.of(
            "today", 0, "now", 0, "date", 1, "duration", 1, "starts with", 2, "ends with", 2, "contains", 2,
            "count", 1, "sum", 1);

    private static void arithmetic(FeelNode node, Map<String, String> types, List<String> errors) {
        switch (node) {
            case FeelNode.Binary binary -> {
                if (ARITHMETIC.contains(binary.operator())) {
                    operand(binary.left(), types, errors);
                    operand(binary.right(), types, errors);

                    if (binary.operator().equals("/")
                            && binary.right() instanceof FeelNode.Num num
                            && num.value() == 0) {
                        errors.add("division by a literal zero");
                    }
                }
                arithmetic(binary.left(), types, errors);
                arithmetic(binary.right(), types, errors);
            }
            case FeelNode.Or or -> {
                arithmetic(or.left(), types, errors);
                arithmetic(or.right(), types, errors);
            }
            case FeelNode.And and -> {
                arithmetic(and.left(), types, errors);
                arithmetic(and.right(), types, errors);
            }
            case FeelNode.Not not -> arithmetic(not.expression(), types, errors);
            case FeelNode.Negate negate -> arithmetic(negate.expression(), types, errors);
            case FeelNode.Call call -> call.arguments().forEach(argument -> arithmetic(argument, types, errors));
            case FeelNode.Quantified quantified -> {
                arithmetic(quantified.collection(), types, errors);
                arithmetic(quantified.predicate(), types, errors);
            }
            case FeelNode.Conditional conditional -> {
                arithmetic(conditional.condition(), types, errors);
                arithmetic(conditional.whenTrue(), types, errors);
                arithmetic(conditional.whenFalse(), types, errors);
            }
            case FeelNode.In in -> {
                arithmetic(in.expression(), types, errors);
                in.list().forEach(item -> arithmetic(item, types, errors));
            }
            default -> {}
        }
    }

    private static void operand(FeelNode node, Map<String, String> types, List<String> errors) {
        if (node instanceof FeelNode.Id id) {
            String type = types.get(id.name());
            if (type != null && (type.equals("string") || type.equals("boolean"))) {
                errors.add("arithmetic on the " + type + " field \"" + id.name() + "\"");
            }
        }
        if (node instanceof FeelNode.Str || node instanceof FeelNode.Bool) {
            errors.add("arithmetic on a " + (node instanceof FeelNode.Str ? "string" : "boolean") + " literal");
        }
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
            case FeelNode.Path path -> bind(path.target(), allowed, errors);
            case FeelNode.Quantified quantified -> {
                bind(quantified.collection(), allowed, errors);
                List<String> inner = new ArrayList<>(allowed);
                inner.add(quantified.variable());
                bind(quantified.predicate(), inner, errors);
            }
            case FeelNode.Negate negate -> bind(negate.expression(), allowed, errors);
            case FeelNode.Conditional conditional -> {
                bind(conditional.condition(), allowed, errors);
                bind(conditional.whenTrue(), allowed, errors);
                bind(conditional.whenFalse(), allowed, errors);
            }
            case FeelNode.In in -> {
                bind(in.expression(), allowed, errors);
                in.list().forEach(item -> bind(item, allowed, errors));
            }
            case FeelNode.Call call -> {
                Integer arity = ARITY.get(call.function());
                if (arity == null) {
                    errors.add("unknown function \"" + call.function() + "\"");
                } else if (arity != call.arguments().size()) {
                    errors.add(call.function() + " takes " + arity + " argument"
                            + (arity == 1 ? "" : "s") + ", got " + call.arguments().size());
                }
                call.arguments().forEach(argument -> bind(argument, allowed, errors));
            }
            default -> {}
        }
    }
}
