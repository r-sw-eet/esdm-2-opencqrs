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

        properties.forEach((name, definition) -> {
            Map<String, Object> def = Raw.record(definition);
            fields.add(new Field(
                    name,
                    def.get("type") == null ? "mixed" : String.valueOf(def.get("type")),
                    required.contains(name),
                    def.get("default"),
                    def.containsKey("default"),
                    false));
        });

        return new Schema(List.copyOf(fields));
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
