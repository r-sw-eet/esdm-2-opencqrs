package io.github.rsweet.esdm2opencqrs.adapter.opencqrs;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rsweet.esdm2opencqrs.model.DocumentLoader;
import io.github.rsweet.esdm2opencqrs.model.Model;
import io.github.rsweet.esdm2opencqrs.model.ModelFactory;
import io.github.rsweet.esdm2opencqrs.model.Raw;
import io.github.rsweet.esdm2opencqrs.model.Yamls;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Extension proposal 0005: a declared mapping must reproduce the documented default exactly, and
 * must actually take effect when it differs - the first check alone would also pass if the
 * annotation were silently ignored.
 */
class ReactionMappingTest {

    private static final String DEFAULT_MAPPING =
            "{ requestId: id, customerName: customerName, product: product, quantity: quantity }";

    @Test
    void aMappingThatStatesTheDefaultChangesNothing(@TempDir Path work) throws IOException {
        Map<String, String> plain = generate(model(work, "plain", null));
        Map<String, String> annotated = generate(model(work, "annotated", DEFAULT_MAPPING));

        assertThat(annotated).isEqualTo(plain);
    }

    @Test
    void aDifferentMappingReachesTheEmittedReaction(@TempDir Path work) throws IOException {
        String swapped = "{ requestId: id, customerName: product, product: customerName, quantity: quantity }";

        String reaction = generate(model(work, "swapped", swapped))
                .get("src/main/java/app/manufacturing/policies/DraftQuoteOnRequestPolicy.java");

        assertThat(reaction)
                .contains("new DraftQuoteCommand(UUID.randomUUID().toString(), event.id(), "
                        + "event.product(), event.customerName(), event.quantity())");
    }

    private static Map<String, String> generate(Path modelDir) {
        Model model = ModelFactory.create(DocumentLoader.loadDirectory(modelDir));
        Map<String, Object> options = new LinkedHashMap<>(Map.of("appName", "manufacturing"));
        return new OpenCqrsAdapter().generate(model, options).files();
    }

    /** A copy of the canonical manufacturing model, optionally carrying the mapping annotation. */
    private static Path model(Path work, String name, String mapping) throws IOException {
        Path source = Path.of("../esdm-2-symfony/examples/manufacturing/model");
        Path target = work.resolve(name);
        Files.createDirectories(target);

        for (Path file : Files.list(source).toList()) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (mapping != null && file.getFileName().toString().equals("manufacturing.esdm.yaml")) {
                text = text.replace(
                        "kind: policy\nname: draft-quote-on-request\nscope:",
                        "kind: policy\nname: draft-quote-on-request\nmetadata:\n  annotations:\n"
                                + "    esdm-extensions.io/mapping: \"" + mapping + "\"\nscope:");
            }
            Files.writeString(target.resolve(file.getFileName()), text, StandardCharsets.UTF_8);
        }
        return target;
    }

    /** Guards the fixture: the canonical model must still carry the policy this test rewrites. */
    @Test
    void theCanonicalModelStillCarriesTheReaction() throws IOException {
        List<String> policies = new ArrayList<>();
        Path file = Path.of("../esdm-2-symfony/examples/manufacturing/model/manufacturing.esdm.yaml");
        for (Object document : Yamls.newYaml().loadAll(Files.readString(file, StandardCharsets.UTF_8))) {
            Map<String, Object> raw = Raw.record(document);
            if ("policy".equals(Raw.string(raw.get("kind"), ""))) {
                policies.add(Raw.string(raw.get("name"), ""));
            }
        }
        assertThat(policies).contains("draft-quote-on-request");
    }
}
