package io.github.rsweet.esdm2opencqrs.model;

import java.util.List;

/**
 * One property of a JSON-Schema {@code object} (aggregate state, command/event data, read-model
 * column).
 *
 * <p>{@code nested} and {@code element} are what an object's {@code properties} and an array's
 * {@code items} become. Both used to be discarded here, which is why FEEL could not bind a path
 * or a collection element - the parser was never the obstacle.
 */
public record Field(
        String name,
        String jsonType,
        boolean required,
        Object defaultValue,
        boolean hasDefault,
        boolean identity,
        List<Field> nested,
        Field element) {

    public Field(String name, String jsonType, boolean required, Object defaultValue, boolean hasDefault, boolean identity) {
        this(name, jsonType, required, defaultValue, hasDefault, identity, List.of(), null);
    }

    public Field withIdentity(boolean isIdentity) {
        return new Field(name, jsonType, required, defaultValue, hasDefault, isIdentity, nested, element);
    }

    /** The field reached by {@code a.b}, or null when this field has no such property. */
    public Field property(String propertyName) {
        return nested.stream().filter(field -> field.name().equals(propertyName)).findFirst().orElse(null);
    }

    public boolean isCollection() {
        return jsonType.equals("array");
    }
}
