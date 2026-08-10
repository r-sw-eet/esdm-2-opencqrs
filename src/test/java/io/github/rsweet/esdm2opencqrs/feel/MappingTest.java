package io.github.rsweet.esdm2opencqrs.feel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The reaction payload mapping of extension proposal 0005. */
class MappingTest {

    @Test
    void parsesEntriesInAuthorOrder() {
        Map<String, FeelNode> mapping = Mapping.parse("{ requestId: id, product: product }");

        assertThat(mapping.keySet()).containsExactly("requestId", "product");
        assertThat(mapping.get("requestId")).isEqualTo(new FeelNode.Id("id"));
    }

    @Test
    void keepsCommasInsideANestedExpression() {
        Map<String, FeelNode> mapping = Mapping.parse("{ tier: status in [\"gold\", \"silver\"], id: id }");

        assertThat(mapping.keySet()).containsExactly("tier", "id");
        assertThat(mapping.get("tier")).isInstanceOf(FeelNode.In.class);
    }

    @Test
    void bindsValuesAgainstTheHandledEventsFields() {
        Map<String, FeelNode> mapping = Mapping.parse("{ requestId: id, name: customerName }");

        assertThat(Mapping.validate(mapping, List.of("id", "customerName"))).isEmpty();
        assertThat(Mapping.validate(mapping, List.of("id")))
                .containsExactly("name: unknown field \"customerName\"");
    }

    @Test
    void rejectsAnythingThatIsNotAContextLiteral() {
        assertThatThrownBy(() -> Mapping.parse("requestId: id"))
                .isInstanceOf(FeelException.class)
                .hasMessageContaining("context literal");
        assertThatThrownBy(() -> Mapping.parse("{ }"))
                .isInstanceOf(FeelException.class)
                .hasMessageContaining("at least one field");
        assertThatThrownBy(() -> Mapping.parse("{ id }"))
                .isInstanceOf(FeelException.class)
                .hasMessageContaining("key: expression");
        assertThatThrownBy(() -> Mapping.parse("{ id: id, id: product }"))
                .isInstanceOf(FeelException.class)
                .hasMessageContaining("twice");
    }
}
