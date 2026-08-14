package io.github.rsweet.esdm2opencqrs.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A parsed JSON-Schema {@code object} ({@code type}, {@code properties}, {@code required}). */
public record Schema(List<Field> fields) {

    public static Schema fromRaw(Map<String, Object> raw) {
        Map<String, Object> properties = Raw.record(raw.get("properties"));
        List<Object> required = Raw.list(raw.get("required"));
        List<Field> fields = new ArrayList<>();

        properties.forEach((name, definition) -> fields.add(field(name, Raw.record(definition), required.contains(name))));

        return new Schema(List.copyOf(fields));
    }

    /** Keeps an object's own properties and an array's element, which FEEL needs to bind against. */
    private static Field field(String name, Map<String, Object> definition, boolean required) {
        String type = definition.get("type") == null ? "mixed" : String.valueOf(definition.get("type"));
        List<Field> nested = List.of();
        Field element = null;

        if (type.equals("object")) {
            Map<String, Object> properties = Raw.record(definition.get("properties"));
            List<Object> innerRequired = Raw.list(definition.get("required"));
            List<Field> inner = new ArrayList<>();
            properties.forEach((inner_name, inner_definition) ->
                    inner.add(field(inner_name, Raw.record(inner_definition), innerRequired.contains(inner_name))));
            nested = List.copyOf(inner);
        }
        if (type.equals("array") && definition.get("items") != null) {
            element = field("item", Raw.record(definition.get("items")), true);
        }

        return new Field(
                name,
                type,
                required,
                definition.get("default"),
                definition.containsKey("default"),
                false,
                nested,
                element);
    }

    public static Schema empty() {
        return new Schema(List.of());
    }

    public Schema withIdentity(String identityField) {
        return new Schema(
                fields.stream().map(f -> f.withIdentity(f.name().equals(identityField))).toList());
    }

    public Field field(String name) {
        return fields.stream().filter(f -> f.name().equals(name)).findFirst().orElse(null);
    }

    public boolean has(String name) {
        return field(name) != null;
    }

    public boolean isEmpty() {
        return fields.isEmpty();
    }
}
