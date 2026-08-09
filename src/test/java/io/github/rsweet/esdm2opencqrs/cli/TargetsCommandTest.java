package io.github.rsweet.esdm2opencqrs.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rsweet.esdm2opencqrs.adapter.Adapter;
import io.github.rsweet.esdm2opencqrs.adapter.AdapterRegistry;
import io.github.rsweet.esdm2opencqrs.adapter.GeneratedProject;
import io.github.rsweet.esdm2opencqrs.model.Model;
import io.github.rsweet.esdm2opencqrs.model.Yamls;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@code targets --json} is the contract esdm-studio reads to populate its target picker; the
 * siblings emit the same three keys.
 */
class TargetsCommandTest {

    @Test
    @SuppressWarnings("unchecked")
    void describesEveryRegisteredTarget() {
        List<Adapter> adapters = AdapterRegistry.withDefaults().all();

        List<Map<String, Object>> parsed = Yamls.newYaml().load(TargetsCommand.toJson(adapters));

        assertThat(parsed).hasSameSizeAs(adapters);
        for (int i = 0; i < adapters.size(); i++) {
            assertThat(parsed.get(i))
                    .containsOnlyKeys("name", "description", "slug")
                    .containsEntry("name", adapters.get(i).name())
                    .containsEntry("description", adapters.get(i).description())
                    .containsEntry("slug", adapters.get(i).slug());
        }
    }

    @Test
    void escapesQuotesSoTheOutputStaysParsable() {
        Adapter quoting = adapter("q", "a \"quoted\" description\\", "q-slug");

        assertThat(TargetsCommand.toJson(List.of(quoting)))
                .isEqualTo("[{\"name\":\"q\",\"description\":\"a \\\"quoted\\\" description\\\\\",\"slug\":\"q-slug\"}]");
    }

    private static Adapter adapter(String name, String description, String slug) {
        return new Adapter() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public String slug() {
                return slug;
            }

            @Override
            public GeneratedProject generate(Model model, Map<String, Object> options) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
