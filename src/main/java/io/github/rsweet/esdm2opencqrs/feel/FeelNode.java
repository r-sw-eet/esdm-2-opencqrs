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

    /** {@code customer.name}: a property of an object-typed field. */
    record Path(FeelNode target, String property) implements FeelNode {}

    /** {@code every x in xs satisfies p} and its {@code some} twin. */
    record Quantified(boolean everyone, String variable, FeelNode collection, FeelNode predicate)
            implements FeelNode {}

    /** {@code if c then a else b}. Lowest precedence, so the branches are whole expressions. */
    record Conditional(FeelNode condition, FeelNode whenTrue, FeelNode whenFalse) implements FeelNode {}

    /**
     * A function call. Carrying arguments is what makes {@code date}, {@code duration},
     * {@code starts with} and {@code contains} expressible at all; {@code today()} and
     * {@code now()} are the same node with an empty list.
     */
    record Call(String function, List<FeelNode> arguments) implements FeelNode {

        public Call(String function) {
            this(function, List.of());
        }
    }
}
