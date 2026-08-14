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
    void stillRejectsBinaryMinusUntilArithmeticLands() {
        assertThatThrownBy(() -> Feel.parse("a - b")).isInstanceOf(FeelException.class);
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
