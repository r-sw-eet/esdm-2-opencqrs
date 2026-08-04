package io.github.rsweet.esdm2opencqrs.support;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Naming helpers. ESDM identifiers are kebab-case ({@code ^[a-z][a-z0-9-]*$}); generated code needs
 * StudlyCase types, camelCase members and snake_case collection names.
 */
public final class Str {

    private Str() {}

    public static String studly(String value) {
        return Arrays.stream(value.split("[-_ ]+"))
                .filter(part -> !part.isEmpty())
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(Collectors.joining());
    }

    public static String camel(String value) {
        String studly = studly(value);
        return studly.isEmpty() ? studly : Character.toLowerCase(studly.charAt(0)) + studly.substring(1);
    }

    public static String snake(String value) {
        return value.replace('-', '_').replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    public static String constant(String value) {
        return snake(value).toUpperCase();
    }
}
