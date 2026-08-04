package io.github.rsweet.esdm2opencqrs.adapter.opencqrs;

import io.github.rsweet.esdm2opencqrs.model.Field;

/** JSON-Schema types to Java types, plus literal rendering for schema defaults. */
public final class JavaTypes {

    private JavaTypes() {}

    public static String of(String jsonType) {
        return switch (jsonType) {
            case "string" -> "String";
            case "boolean" -> "Boolean";
            case "number" -> "Double";
            case "integer" -> "Long";
            case "array" -> "java.util.List<Object>";
            case "object" -> "java.util.Map<String, Object>";
            default -> "Object";
        };
    }

    /** The Java literal for a field's schema default, or a type-appropriate empty value. */
    public static String defaultLiteral(Field field) {
        Object value = field.defaultValue();
        if (value == null) {
            return switch (field.jsonType()) {
                case "boolean" -> "false";
                case "number" -> "0.0d";
                case "integer" -> "0L";
                case "string" -> "\"\"";
                default -> "null";
            };
        }

        return switch (field.jsonType()) {
            case "boolean" -> Boolean.parseBoolean(String.valueOf(value)) ? "true" : "false";
            case "number" -> Double.parseDouble(String.valueOf(value)) + "d";
            case "integer" -> Long.parseLong(String.valueOf(value)) + "L";
            case "string" -> Q.string(String.valueOf(value));
            default -> "null";
        };
    }

    public static String nullLiteral(Field field) {
        return field.required() || field.hasDefault() ? defaultLiteral(field) : "null";
    }
}
