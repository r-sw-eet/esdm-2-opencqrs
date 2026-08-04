package io.github.rsweet.esdm2opencqrs.adapter.opencqrs;

import io.github.rsweet.esdm2opencqrs.feel.FeelNode;
import io.github.rsweet.esdm2opencqrs.support.Str;
import java.util.stream.Collectors;

/** Compiles a FEEL AST to a Java boolean expression over the write model {@code state}. */
public final class FeelJava {

    private FeelJava() {}

    public static String compile(FeelNode node, String basePackage) {
        return switch (node) {
            case FeelNode.Or or -> "(" + compile(or.left(), basePackage) + " || " + compile(or.right(), basePackage) + ")";
            case FeelNode.And and ->
                "(" + compile(and.left(), basePackage) + " && " + compile(and.right(), basePackage) + ")";
            case FeelNode.Not not -> "!(" + compile(not.expression(), basePackage) + ")";
            case FeelNode.Binary binary -> binaryExpression(binary, basePackage);
            case FeelNode.In in -> "java.util.List.of("
                    + in.list().stream().map(item -> compile(item, basePackage)).collect(Collectors.joining(", "))
                    + ").contains(" + compile(in.expression(), basePackage) + ")";
            case FeelNode.Id id -> identifier(id.name());
            case FeelNode.Str str -> Q.string(str.value());
            case FeelNode.Num num -> number(num.value());
            case FeelNode.Bool bool -> bool.value() ? "true" : "false";
            case FeelNode.Call call ->
                call.function().equals("today")
                        ? "java.time.LocalDate.now().toString()"
                        : "java.time.Instant.now().toString()";
        };
    }

    private static String binaryExpression(FeelNode.Binary binary, String basePackage) {
        String left = compile(binary.left(), basePackage);
        String right = compile(binary.right(), basePackage);

        // Both go through Guards: FEEL compares numbers by value across box types, and orders
        // dates-as-strings as well as numbers - Java has no operator that spans either case.
        String guards = basePackage + ".support.Guards.";
        return switch (binary.operator()) {
            case "=" -> guards + "equal(" + left + ", " + right + ")";
            case "!=" -> "!" + guards + "equal(" + left + ", " + right + ")";
            default -> "(" + guards + "compare(" + left + ", " + right + ") " + binary.operator() + " 0)";
        };
    }

    private static String identifier(String name) {
        if (name.equals("status")) {
            return "(state.status() == null ? \"\" : state.status())";
        }
        return "state." + Str.camel(name) + "()";
    }

    private static String number(double value) {
        return value == Math.rint(value) && !Double.isInfinite(value)
                ? String.valueOf((long) value)
                : String.valueOf(value);
    }
}
