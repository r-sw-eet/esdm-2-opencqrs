package io.github.rsweet.esdm2opencqrs.model;

/** One property of a JSON-Schema {@code object} (aggregate state, command/event data, read-model column). */
public record Field(String name, String jsonType, boolean required, Object defaultValue, boolean hasDefault, boolean identity) {

    public Field withIdentity(boolean isIdentity) {
        return new Field(name, jsonType, required, defaultValue, hasDefault, isIdentity);
    }
}
