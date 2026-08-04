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
                .isEqualTo("(app.demo.support.Guards.compare(state.paidAmount(), state.total()) >= 0)");
    }

    @Test
    void statusFallsBackToAnEmptyStringWhenUnset() {
        assertThat(compile("status = \"open\""))
                .isEqualTo("app.demo.support.Guards.equal((state.status() == null ? \"\" : state.status()), \"open\")");
    }

    @Test
    void compilesTemporalFunctions() {
        assertThat(compile("validUntil >= today()"))
                .isEqualTo("(app.demo.support.Guards.compare(state.validUntil(),"
                        + " java.time.LocalDate.now().toString()) >= 0)");
    }

    @Test
    void compilesBooleanCombinators() {
        assertThat(compile("not(a = 1) and b in [2, 3]"))
                .isEqualTo("(!(app.demo.support.Guards.equal(state.a(), 1)) &&"
                        + " java.util.List.of(2, 3).contains(state.b()))");
    }
}
