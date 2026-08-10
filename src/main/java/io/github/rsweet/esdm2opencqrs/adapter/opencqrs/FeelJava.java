package io.github.rsweet.esdm2opencqrs.adapter.opencqrs;

import io.github.rsweet.esdm2opencqrs.feel.FeelNode;
import io.github.rsweet.esdm2opencqrs.support.Str;
import java.util.stream.Collectors;

/** Compiles a FEEL AST to a Java boolean expression over the write model {@code state}. */
public final class FeelJava {

    private FeelJava() {}

    public static String compile(FeelNode node, String basePackage) {
        return compile(node, basePackage, "state");
    }

    /**
     * Same compiler over a different receiver: guards read the write model {@code state}, a
     * reaction mapping (proposal 0005) reads the handled {@code event}.
     */
    public static String compile(FeelNode node, String basePackage, String receiver) {
        return compileNode(node, basePackage, receiver);
    }

    private static String compileNode(FeelNode node, String basePackage, String receiver) {
        return switch (node) {
            case FeelNode.Or or -> "(" + compileNode(or.left(), basePackage, receiver) + " || " + compileNode(or.right(), basePackage, receiver) + ")";
            case FeelNode.And and ->
                "(" + compileNode(and.left(), basePackage, receiver) + " && " + compileNode(and.right(), basePackage, receiver) + ")";
            case FeelNode.Not not -> "!(" + compileNode(not.expression(), basePackage, receiver) + ")";
            case FeelNode.Binary binary -> binaryExpression(binary, basePackage, receiver);
            case FeelNode.In in -> "java.util.List.of("
                    + in.list().stream().map(item -> compileNode(item, basePackage, receiver)).collect(Collectors.joining(", "))
                    + ").contains(" + compileNode(in.expression(), basePackage, receiver) + ")";
            case FeelNode.Id id -> identifier(id.name(), receiver);
            case FeelNode.Str str -> Q.string(str.value());
            case FeelNode.Num num -> number(num.value());
            case FeelNode.Bool bool -> bool.value() ? "true" : "false";
            case FeelNode.Call call ->
                call.function().equals("today")
                        ? "java.time.LocalDate.now().toString()"
                        : "java.time.Instant.now().toString()";
        };
    }

    private static String binaryExpression(FeelNode.Binary binary, String basePackage, String receiver) {
        String left = compileNode(binary.left(), basePackage, receiver);
        String right = compileNode(binary.right(), basePackage, receiver);

        // Both go through Guards: FEEL compares numbers by value across box types, and orders
        // dates-as-strings as well as numbers - Java has no operator that spans either case.
        String guards = basePackage + ".support.Guards.";
        return switch (binary.operator()) {
            case "=" -> guards + "equal(" + left + ", " + right + ")";
            case "!=" -> "!" + guards + "equal(" + left + ", " + right + ")";
            default -> "(" + guards + "compare(" + left + ", " + right + ") " + binary.operator() + " 0)";
        };
    }

    private static String identifier(String name, String receiver) {
        // Only the write model's status can be unset mid-lifecycle; an event carries what it carries.
        if (receiver.equals("state") && name.equals("status")) {
            return "(state.status() == null ? \"\" : state.status())";
        }
        return receiver + "." + Str.camel(name) + "()";
    }

    private static String number(double value) {
        return value == Math.rint(value) && !Double.isInfinite(value)
                ? String.valueOf((long) value)
                : String.valueOf(value);
    }
}
