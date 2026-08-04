package io.github.rsweet.esdm2opencqrs.model;

import java.util.regex.Pattern;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

/**
 * YAML loading for ESDM documents. SnakeYAML defaults to YAML 1.1, where the bare words
 * {@code on/off/yes/no} resolve to booleans - which would silently turn the state-machine key
 * {@code on:} into {@code true:} and drop every transition. The sibling generators parse with YAML
 * 1.2 core-schema semantics, so this narrows the boolean resolver to {@code true}/{@code false} to
 * read the same model the same way.
 */
public final class Yamls {

    private Yamls() {}

    public static Yaml newYaml() {
        LoaderOptions loaderOptions = new LoaderOptions();
        DumperOptions dumperOptions = new DumperOptions();
        return new Yaml(
                new SafeConstructor(loaderOptions),
                new Representer(dumperOptions),
                dumperOptions,
                loaderOptions,
                new CoreSchemaResolver());
    }

    private static final class CoreSchemaResolver extends Resolver {

        private static final Pattern STRICT_BOOL = Pattern.compile("^(?:true|True|TRUE|false|False|FALSE)$");

        @Override
        protected void addImplicitResolvers() {
            addImplicitResolver(Tag.BOOL, STRICT_BOOL, "tTfF");
            addImplicitResolver(Tag.INT, Resolver.INT, "-+0123456789");
            addImplicitResolver(Tag.FLOAT, Resolver.FLOAT, "-+0123456789.");
            addImplicitResolver(Tag.MERGE, Resolver.MERGE, "<");
            addImplicitResolver(Tag.NULL, Resolver.NULL, "~nN\0");
            addImplicitResolver(Tag.NULL, Resolver.EMPTY, null);
            addImplicitResolver(Tag.TIMESTAMP, Resolver.TIMESTAMP, "0123456789");
        }
    }
}
