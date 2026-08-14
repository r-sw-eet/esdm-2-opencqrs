package io.github.rsweet.esdm2opencqrs.feel;

import java.util.List;

/** AST of the FEEL subset (proposal 0002). */
public sealed interface FeelNode {

    record Or(FeelNode left, FeelNode right) implements FeelNode {}

    record And(FeelNode left, FeelNode right) implements FeelNode {}

    record Not(FeelNode expression) implements FeelNode {}

    record Binary(String operator, FeelNode left, FeelNode right) implements FeelNode {}

    record In(FeelNode expression, List<FeelNode> list) implements FeelNode {}

    record Id(String name) implements FeelNode {}

    record Str(String value) implements FeelNode {}

    record Num(double value) implements FeelNode {}

    record Bool(boolean value) implements FeelNode {}

    /** The FEEL {@code null} literal. Without it, `null` lexes as a field name. */
    record NullLiteral() implements FeelNode {}

    /** Unary minus. A negative *literal* folds into {@link Num} instead. */
    record Negate(FeelNode expression) implements FeelNode {}

    /** {@code if c then a else b}. Lowest precedence, so the branches are whole expressions. */
    record Conditional(FeelNode condition, FeelNode whenTrue, FeelNode whenFalse) implements FeelNode {}

    /** The niladic functions {@code today()} and {@code now()}. */
    record Call(String function) implements FeelNode {}
}
