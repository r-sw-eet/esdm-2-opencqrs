package io.github.rsweet.esdm2opencqrs.adapter.opencqrs;

import io.github.rsweet.esdm2opencqrs.feel.FeelNode;
import io.github.rsweet.esdm2opencqrs.support.Str;
import java.util.List;
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
        return compileNode(node, basePackage, receiver, null);
    }

    private static String compileNode(FeelNode node, String basePackage, String receiver) {
        return compileNode(node, basePackage, receiver, null);
    }

    /**
     * {@code local} is the variable a quantifier binds: inside {@code every x in xs satisfies …}
     * the identifier {@code x} is the lambda parameter rather than a field of the receiver.
     */
    private static String compileNode(FeelNode node, String basePackage, String receiver, String local) {
        return switch (node) {
            case FeelNode.Or or -> "(" + compileNode(or.left(), basePackage, receiver, local) + " || " + compileNode(or.right(), basePackage, receiver, local) + ")";
            case FeelNode.And and ->
                "(" + compileNode(and.left(), basePackage, receiver, local) + " && " + compileNode(and.right(), basePackage, receiver, local) + ")";
            case FeelNode.Not not -> "!(" + compileNode(not.expression(), basePackage, receiver, local) + ")";
            case FeelNode.Binary binary -> binaryExpression(binary, basePackage, receiver, local);
            case FeelNode.In in -> "java.util.List.of("
                    + in.list().stream().map(item -> compileNode(item, basePackage, receiver, local)).collect(Collectors.joining(", "))
                    + ").contains(" + compileNode(in.expression(), basePackage, receiver, local) + ")";
            case FeelNode.Id id -> id.name().equals(local) ? local : identifier(id.name(), receiver);
            case FeelNode.Path path -> basePackage + ".support.Guards.property("
                    + compileNode(path.target(), basePackage, receiver, local) + ", \"" + path.property() + "\")";
            case FeelNode.Quantified quantified -> compileNode(quantified.collection(), basePackage, receiver, local)
                    + ".stream()." + (quantified.everyone() ? "allMatch" : "anyMatch") + "("
                    + quantified.variable() + " -> "
                    + compileNode(quantified.predicate(), basePackage, receiver, quantified.variable()) + ")";
            case FeelNode.Str str -> Q.string(str.value());
            case FeelNode.Num num -> number(num.value());
            case FeelNode.Bool bool -> bool.value() ? "true" : "false";
            case FeelNode.NullLiteral ignored -> "null";
            case FeelNode.Negate negate -> "-(" + compileNode(negate.expression(), basePackage, receiver, local) + ")";
            case FeelNode.Conditional conditional -> "("
                    + compileNode(conditional.condition(), basePackage, receiver, local) + " ? "
                    + compileNode(conditional.whenTrue(), basePackage, receiver, local) + " : "
                    + compileNode(conditional.whenFalse(), basePackage, receiver, local) + ")";
            case FeelNode.Call call -> call(call, basePackage, receiver, local);
        };
    }

    private static String binaryExpression(FeelNode.Binary binary, String basePackage, String receiver, String local) {
        String left = compileNode(binary.left(), basePackage, receiver, local);
        String right = compileNode(binary.right(), basePackage, receiver, local);

        // Both go through Guards: FEEL compares numbers by value across box types, and orders
        // dates-as-strings as well as numbers - Java has no operator that spans either case.
        String guards = basePackage + ".support.Guards.";

        // Arithmetic never compiles to a bare Java operator: `7 / 2` on two longs is 3 here and
        // 3.5 in every sibling language, so the helper keeps the whole expression in the real
        // number domain (proposal 0002, amendment 2026-08-14).
        // `validUntil + duration("P14D")` is a date shift, not a sum - the temporal row 0002 has
        // claimed since v1, reachable only now that calls can carry arguments.
        if (binary.operator().equals("+") && isDuration(binary.right())) {
            return guards + "datePlusDays(" + left + ", " + right + ")";
        }
        if (binary.operator().equals("-") && isDuration(binary.right())) {
            return guards + "datePlusDays(" + left + ", -(" + right + "))";
        }

        switch (binary.operator()) {
            case "+":
                return guards + "add(" + left + ", " + right + ")";
            case "-":
                return guards + "subtract(" + left + ", " + right + ")";
            case "*":
                return guards + "multiply(" + left + ", " + right + ")";
            case "/":
                return guards + "divide(" + left + ", " + right + ")";
            default:
                break;
        }

        return switch (binary.operator()) {
            case "=" -> guards + "equal(" + left + ", " + right + ")";
            case "!=" -> "!" + guards + "equal(" + left + ", " + right + ")";
            default -> guards + "ordered(\"" + binary.operator() + "\", " + left + ", " + right + ")";
        };
    }

    /**
     * The supported calls. `date` is the identity on an ISO-8601 string, because this family
     * already compares dates as ISO strings and lexical order is chronological there; `duration`
     * yields whole days, which is all {@link Guards#datePlusDays} needs.
     */
    private static String call(FeelNode.Call call, String basePackage, String receiver, String local) {
        String guards = basePackage + ".support.Guards.";
        List<String> arguments =
                call.arguments().stream().map(argument -> compileNode(argument, basePackage, receiver, local)).toList();

        return switch (call.function()) {
            case "today" -> "java.time.LocalDate.now().toString()";
            case "now" -> "java.time.Instant.now().toString()";
            case "date" -> guards + "date(" + arguments.get(0) + ")";
            case "duration" -> String.valueOf(durationDays(call.arguments().get(0)));
            case "starts with" -> guards + "startsWith(" + arguments.get(0) + ", " + arguments.get(1) + ")";
            case "ends with" -> guards + "endsWith(" + arguments.get(0) + ", " + arguments.get(1) + ")";
            case "contains" -> guards + "contains(" + arguments.get(0) + ", " + arguments.get(1) + ")";
            case "count" -> guards + "count(" + arguments.get(0) + ")";
            case "sum" -> guards + "sum(" + arguments.get(0) + ")";
            default -> throw new IllegalStateException("unsupported function " + call.function());
        };
    }

    /**
     * A duration is always a literal, so its day count is computed here rather than by emitted
     * code. Weeks are days; months and years are not, since their length depends on the date.
     */
    static long durationDays(FeelNode node) {
        if (!(node instanceof FeelNode.Str literal)) {
            throw new IllegalStateException("duration() takes a string literal");
        }
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("^P(\\d+)([DW])$").matcher(literal.value());
        if (!matcher.matches()) {
            throw new IllegalStateException("unsupported duration \"" + literal.value() + "\" - use P<n>D or P<n>W");
        }
        long amount = Long.parseLong(matcher.group(1));

        return matcher.group(2).equals("W") ? amount * 7 : amount;
    }

    private static boolean isDuration(FeelNode node) {
        return node instanceof FeelNode.Call call && call.function().equals("duration");
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
