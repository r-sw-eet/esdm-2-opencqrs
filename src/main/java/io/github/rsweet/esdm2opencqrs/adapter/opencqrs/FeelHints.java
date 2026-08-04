package io.github.rsweet.esdm2opencqrs.adapter.opencqrs;

import io.github.rsweet.esdm2opencqrs.feel.Feel;
import io.github.rsweet.esdm2opencqrs.feel.FeelException;
import io.github.rsweet.esdm2opencqrs.feel.FeelNode;
import io.github.rsweet.esdm2opencqrs.model.Aggregate;
import io.github.rsweet.esdm2opencqrs.model.StateMachine;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-field authoring hints distilled from the FEEL guards of an aggregate, so a domain console can
 * render sensible inputs (a date picker, a value list) for command fields the rules constrain.
 */
final class FeelHints {

    private FeelHints() {}

    static Map<String, Object> of(Aggregate aggregate) {
        StateMachine machine = aggregate.stateMachine();
        if (machine == null) {
            return Map.of();
        }

        Map<String, Accumulator> accumulators = new LinkedHashMap<>();
        for (StateMachine.Admit admit : machine.admits()) {
            if (admit.when() == null || admit.when().isEmpty()) {
                continue;
            }
            try {
                collect(Feel.parse(admit.when()), admit.when(), accumulators);
            } catch (FeelException e) {
                // A malformed guard is reported by the FEEL gate; it must not break catalog emission.
            }
        }

        Map<String, Object> hints = new LinkedHashMap<>();
        accumulators.forEach((field, accumulator) -> {
            Map<String, Object> hint = new LinkedHashMap<>();
            hint.put("temporal", accumulator.temporal);
            hint.put("values", List.copyOf(accumulator.values));
            hint.put("rules", List.copyOf(accumulator.rules));
            hints.put(field, hint);
        });
        return hints;
    }

    private static void collect(FeelNode node, String rule, Map<String, Accumulator> accumulators) {
        switch (node) {
            case FeelNode.And and -> {
                collect(and.left(), rule, accumulators);
                collect(and.right(), rule, accumulators);
            }
            case FeelNode.Or or -> {
                collect(or.left(), rule, accumulators);
                collect(or.right(), rule, accumulators);
            }
            case FeelNode.Not not -> collect(not.expression(), rule, accumulators);
            case FeelNode.Binary binary -> {
                recordOperand(binary.left(), binary.right(), rule, accumulators);
                recordOperand(binary.right(), binary.left(), rule, accumulators);
            }
            case FeelNode.In in -> {
                if (in.expression() instanceof FeelNode.Id id) {
                    in.list().forEach(item -> addLiteral(accumulators, id.name(), item, rule));
                }
            }
            default -> {}
        }
    }

    private static void recordOperand(
            FeelNode field, FeelNode other, String rule, Map<String, Accumulator> accumulators) {
        if (!(field instanceof FeelNode.Id id)) {
            return;
        }
        if (other instanceof FeelNode.Call call) {
            Accumulator accumulator = accumulators.computeIfAbsent(id.name(), key -> new Accumulator());
            accumulator.temporal = call.function().equals("now") ? "datetime" : "date";
            accumulator.rules.add(rule);
            return;
        }
        addLiteral(accumulators, id.name(), other, rule);
    }

    private static void addLiteral(
            Map<String, Accumulator> accumulators, String field, FeelNode literal, String rule) {
        String value = switch (literal) {
            case FeelNode.Str str -> str.value();
            case FeelNode.Num num ->
                num.value() == Math.rint(num.value())
                        ? String.valueOf((long) num.value())
                        : String.valueOf(num.value());
            case FeelNode.Bool bool -> bool.value() ? "true" : "false";
            default -> null;
        };
        if (value == null) {
            return;
        }
        Accumulator accumulator = accumulators.computeIfAbsent(field, key -> new Accumulator());
        accumulator.values.add(value);
        accumulator.rules.add(rule);
    }

    private static final class Accumulator {
        private String temporal;
        private final Set<String> values = new LinkedHashSet<>();
        private final Set<String> rules = new LinkedHashSet<>();
    }
}
