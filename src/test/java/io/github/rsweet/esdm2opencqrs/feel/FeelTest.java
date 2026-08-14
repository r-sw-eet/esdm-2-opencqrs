package io.github.rsweet.esdm2opencqrs.feel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class FeelTest {

    @Test
    void parsesNullAsALiteralAndNotAsAField() {
        assertThat(Feel.parse("cancelledAt = null"))
                .isInstanceOfSatisfying(FeelNode.Binary.class, binary -> {
                    assertThat(binary.left()).isEqualTo(new FeelNode.Id("cancelledAt"));
                    assertThat(binary.right()).isEqualTo(new FeelNode.NullLiteral());
                });
    }

    @Test
    void bindsNullWithoutBlamingTheModel() {
        // `null` used to lex as a name, so this reported: unknown field "null".
        assertThat(Feel.validate(Feel.parse("cancelledAt = null"), java.util.List.of("cancelledAt")))
                .isEmpty();
    }

    @Test
    void foldsANegativeLiteralSoTheEmittedCodeReadsNaturally() {
        assertThat(Feel.parse("amount > -1"))
                .isInstanceOfSatisfying(FeelNode.Binary.class, binary ->
                        assertThat(binary.right()).isEqualTo(new FeelNode.Num(-1)));
    }

    @Test
    void desugarsBetweenIntoTwoComparisons() {
        assertThat(Feel.parse("qty between 1 and 10"))
                .isEqualTo(Feel.parse("qty >= 1 and qty <= 10"));
    }

    @Test
    void desugarsARangeIntoTwoComparisons() {
        assertThat(Feel.parse("qty in [1..10]"))
                .isEqualTo(Feel.parse("qty >= 1 and qty <= 10"));
    }

    @Test
    void keepsMembershipAsMembership() {
        assertThat(Feel.parse("status in [\"a\", \"b\"]")).isInstanceOf(FeelNode.In.class);
    }

    @Test
    void theArithmeticGateRejectsWhatTheAmendmentSaysItShould() {
        java.util.List<String> allowed = java.util.List.of("amount", "quantity", "status");
        java.util.Map<String, String> types =
                java.util.Map.of("amount", "number", "quantity", "integer", "status", "string");

        assertThat(Feel.validate(Feel.parse("amount * quantity >= 5000"), allowed, types)).isEmpty();
        assertThat(Feel.validate(Feel.parse("status * 2 > 1"), allowed, types))
                .containsExactly("arithmetic on the string field \"status\"");
        assertThat(Feel.validate(Feel.parse("amount / 0 > 1"), allowed, types))
                .containsExactly("division by a literal zero");
    }

    @Test
    void arithmeticBindsTighterThanComparison() {
        assertThat(Feel.parse("a - b > 1"))
                .isInstanceOfSatisfying(FeelNode.Binary.class, comparison -> {
                    assertThat(comparison.operator()).isEqualTo(">");
                    assertThat(comparison.left()).isInstanceOf(FeelNode.Binary.class);
                });
    }

    @Test
    void multiplicationBindsTighterThanAddition() {
        assertThat(Feel.parse("x = 1 + 2 * 3")).isEqualTo(Feel.parse("x = 1 + (2 * 3)"));
        assertThat(Feel.parse("x = 1 + 2 * 3")).isNotEqualTo(Feel.parse("x = (1 + 2) * 3"));
    }

    @Test
    void parsesComparison() {
        assertThat(Feel.parse("paidAmount >= total"))
                .isInstanceOfSatisfying(FeelNode.Binary.class, binary -> {
                    assertThat(binary.operator()).isEqualTo(">=");
                    assertThat(binary.left()).isEqualTo(new FeelNode.Id("paidAmount"));
                    assertThat(binary.right()).isEqualTo(new FeelNode.Id("total"));
                });
    }

    @Test
    void parsesConjunctionWithPrecedenceBelowComparison() {
        assertThat(Feel.parse("a = 1 and b = 2")).isInstanceOf(FeelNode.And.class);
    }

    @Test
    void parsesMembership() {
        assertThat(Feel.parse("status in [\"open\", \"sent\"]"))
                .isInstanceOfSatisfying(FeelNode.In.class, in -> assertThat(in.list()).hasSize(2));
    }

    @Test
    void parsesNiladicFunctions() {
        assertThat(Feel.parse("validUntil >= today()"))
                .isInstanceOfSatisfying(
                        FeelNode.Binary.class, binary -> assertThat(binary.right()).isEqualTo(new FeelNode.Call("today")));
    }

    @Test
    void rejectsUnexpectedCharacters() {
        assertThatThrownBy(() -> Feel.parse("a $ b")).isInstanceOf(FeelException.class);
    }

    @Test
    void bindsIdentifiersAgainstAllowedFields() {
        FeelNode ast = Feel.parse("paidAmount >= total");
        assertThat(Feel.validate(ast, List.of("paidAmount", "total"))).isEmpty();
        assertThat(Feel.validate(ast, List.of("paidAmount"))).containsExactly("unknown field \"total\"");
    }
}
