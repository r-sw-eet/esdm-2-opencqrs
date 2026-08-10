package io.github.rsweet.esdm2opencqrs.feel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A FEEL context literal - {@code { requestId: id, cents: durationSeconds * rate }} - as used by
 * extension proposal 0005 to say what a reaction's emitted command carries. Values are ordinary
 * FEEL expressions bound against the handled event's payload, so the whole expression language
 * comes from {@link Feel} and this class only splits the context into its entries.
 */
public final class Mapping {

    private Mapping() {}

    /** Parses a context literal into key -&gt; value expression, preserving author order. */
    public static Map<String, FeelNode> parse(String source) {
        String body = source.trim();
        if (!body.startsWith("{") || !body.endsWith("}")) {
            throw new FeelException("A mapping must be a FEEL context literal: { key: expression, ... }");
        }

        Map<String, FeelNode> entries = new LinkedHashMap<>();
        for (String entry : split(body.substring(1, body.length() - 1))) {
            if (entry.isBlank()) {
                continue;
            }
            int colon = colonAt(entry);
            if (colon < 0) {
                throw new FeelException("Mapping entry is not \"key: expression\": \"" + entry.trim() + "\"");
            }
            String key = entry.substring(0, colon).trim();
            if (!key.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new FeelException("Mapping key is not a field name: \"" + key + "\"");
            }
            if (entries.containsKey(key)) {
                throw new FeelException("Mapping assigns \"" + key + "\" twice");
            }
            entries.put(key, Feel.parse(entry.substring(colon + 1)));
        }

        if (entries.isEmpty()) {
            throw new FeelException("A mapping must assign at least one field");
        }
        return entries;
    }

    /** Binding errors for every value expression, prefixed with the key they came from. */
    public static List<String> validate(Map<String, FeelNode> mapping, List<String> allowedFields) {
        List<String> errors = new ArrayList<>();
        mapping.forEach((key, value) ->
                Feel.validate(value, allowedFields).forEach(error -> errors.add(key + ": " + error)));
        return errors;
    }

    /** Splits on top-level commas only, so a nested list or call keeps its own separators. */
    private static List<String> split(String body) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        int start = 0;

        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '"') {
                inString = !inString;
            } else if (!inString && (c == '(' || c == '[' || c == '{')) {
                depth++;
            } else if (!inString && (c == ')' || c == ']' || c == '}')) {
                depth--;
            } else if (!inString && depth == 0 && c == ',') {
                parts.add(body.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(body.substring(start));
        return parts;
    }

    /** The key separator, skipping any colon inside a nested expression or a string. */
    private static int colonAt(String entry) {
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < entry.length(); i++) {
            char c = entry.charAt(i);
            if (c == '"') {
                inString = !inString;
            } else if (!inString && (c == '(' || c == '[' || c == '{')) {
                depth++;
            } else if (!inString && (c == ')' || c == ']' || c == '}')) {
                depth--;
            } else if (!inString && depth == 0 && c == ':') {
                return i;
            }
        }
        return -1;
    }
}
