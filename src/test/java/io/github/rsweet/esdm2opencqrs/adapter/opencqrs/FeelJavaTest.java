package io.github.rsweet.esdm2opencqrs.adapter.opencqrs;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rsweet.esdm2opencqrs.feel.Feel;
import org.junit.jupiter.api.Test;

class FeelJavaTest {

    private static String compile(String expression) {
        return FeelJava.compile(Feel.parse(expression), "app.demo");
    }

    @Test
    void equalityComparesNumbersByValueNotByBoxType() {
        assertThat(compile("defects = 0")).isEqualTo("app.demo.support.Guards.equal(state.defects(), 0)");
    }

    @Test
    void orderingGoesThroughTheComparisonHelper() {
        assertThat(compile("paidAmount >= total"))
                .isEqualTo("app.demo.support.Guards.ordered(\">=\", state.paidAmount(), state.total())");
    }

    @Test
    void statusFallsBackToAnEmptyStringWhenUnset() {
        assertThat(compile("status = \"open\""))
                .isEqualTo("app.demo.support.Guards.equal((state.status() == null ? \"\" : state.status()), \"open\")");
    }

    @Test
    void compilesTemporalFunctions() {
        assertThat(compile("validUntil >= today()"))
                .isEqualTo("app.demo.support.Guards.ordered(\">=\", state.validUntil(),"
                        + " java.time.LocalDate.now().toString())");
    }

    @Test
    void arithmeticNeverCompilesToABareJavaOperator() {
        // `amount * quantity` on two longs would be exact here but `/` would be integer division,
        // so the whole expression stays in the helper's real-number domain.
        assertThat(compile("amount * quantity >= 5000"))
                .isEqualTo("app.demo.support.Guards.ordered(\">=\","
                        + " app.demo.support.Guards.multiply(state.amount(), state.quantity()), 5000)");
        assertThat(compile("total / count > 1"))
                .isEqualTo("app.demo.support.Guards.ordered(\">\","
                        + " app.demo.support.Guards.divide(state.total(), state.count()), 1)");
    }

    @Test
    void compilesAConditionalAndAUnaryMinus() {
        assertThat(compile("if a then 1 else 2"))
                .isEqualTo("(app.demo.support.Guards.equal(state.a(), true) ? 1 : 2)".replace(
                        "app.demo.support.Guards.equal(state.a(), true)", "state.a()"));
        assertThat(compile("-amount > 1"))
                .isEqualTo("app.demo.support.Guards.ordered(\">\", -(state.amount()), 1)");
    }

    @Test
    void compilesBooleanCombinators() {
        assertThat(compile("not(a = 1) and b in [2, 3]"))
                .isEqualTo("(!(app.demo.support.Guards.equal(state.a(), 1)) &&"
                        + " java.util.List.of(2, 3).contains(state.b()))");
    }
}
