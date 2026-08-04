package io.github.rsweet.esdm2opencqrs.conformance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The C4 normalization and comparison rules (conformance README steps 6 and 7). Golden files are
 * byte-comparisons after normalization, so every rule here has to match the other runners exactly.
 */
public final class Observations {

    private Observations() {}

    /** Replaces every captured value with its {@code «NAME»} placeholder, wherever it appears as a string. */
    public static Object mask(Object value, Map<String, String> captures) {
        return switch (value) {
            case String string -> {
                for (Map.Entry<String, String> capture : captures.entrySet()) {
                    if (string.equals(capture.getValue())) {
                        yield "«" + capture.getKey() + "»";
                    }
                }
                yield string;
            }
            case Map<?, ?> map -> {
                Map<String, Object> out = new LinkedHashMap<>();
                map.forEach((key, entry) -> out.put(String.valueOf(key), mask(entry, captures)));
                yield out;
            }
            case List<?> list -> list.stream().map(item -> mask(item, captures)).toList();
            case null, default -> value;
        };
    }

    /** snake_case object keys to camelCase ({@code _x} to {@code X}); keys without underscores are untouched. */
    public static Object camelizeKeys(Object value) {
        return switch (value) {
            case Map<?, ?> map -> {
                Map<String, Object> out = new LinkedHashMap<>();
                map.forEach((key, entry) -> out.put(camelize(String.valueOf(key)), camelizeKeys(entry)));
                yield out;
            }
            case List<?> list -> list.stream().map(Observations::camelizeKeys).toList();
            case null, default -> value;
        };
    }

    static String camelize(String key) {
        if (key.indexOf('_') < 0) {
            return key;
        }
        StringBuilder out = new StringBuilder();
        boolean upper = false;
        for (char c : key.toCharArray()) {
            if (c == '_') {
                upper = true;
            } else {
                out.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return out.toString();
    }

    /** List bodies are order-insensitive; they sort by their canonical JSON. */
    public static Object sortIfList(Object value) {
        if (value instanceof List<?> list) {
            List<Object> sorted = new ArrayList<>(list);
            sorted.sort(Comparator.comparing(Observations::canonical));
            return sorted;
        }
        return value;
    }

    /** Canonical JSON: keys sorted lexicographically, no whitespace. */
    public static String canonical(Object value) {
        StringBuilder out = new StringBuilder();
        writeCanonical(value, out);
        return out.toString();
    }

    private static void writeCanonical(Object value, StringBuilder out) {
        switch (value) {
            case null -> out.append("null");
            case Map<?, ?> map -> {
                Map<String, Object> sorted = new TreeMap<>();
                map.forEach((key, entry) -> sorted.put(String.valueOf(key), entry));
                out.append('{');
                boolean first = true;
                for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    out.append(quote(entry.getKey())).append(':');
                    writeCanonical(entry.getValue(), out);
                }
                out.append('}');
            }
            case List<?> list -> {
                out.append('[');
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) {
                        out.append(',');
                    }
                    writeCanonical(list.get(i), out);
                }
                out.append(']');
            }
            case String string -> out.append(quote(string));
            case Boolean bool -> out.append(bool);
            case Number number -> out.append(number);
            default -> out.append(quote(String.valueOf(value)));
        }
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.append('"').toString();
    }

    /**
     * The {@code events} checkpoint row shape. The store event id and timestamp are dropped (they
     * are per-stack), window order (newest first) is preserved.
     */
    public static List<Object> normalizeEventRows(Object body, Map<String, String> captures) {
        if (!(body instanceof List<?> rows)) {
            return List.of();
        }
        List<Object> out = new ArrayList<>();
        for (Object raw : rows) {
            if (!(raw instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            map.forEach((key, value) -> row.put(String.valueOf(key), value));

            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("aggregate", String.valueOf(row.get("aggregate")).toLowerCase());
            normalized.put("aggregateId", mask(row.get("aggregate_id"), captures));
            normalized.put("event", eventLabel(String.valueOf(row.get("event"))));
            normalized.put("playhead", row.get("playhead"));
            normalized.put("payload", camelizeKeys(mask(row.get("payload"), captures)));
            out.add(normalized);
        }
        return out;
    }

    /** The stack's event identifier reduced to its last dot-segment, underscores to dashes, lowercased. */
    static String eventLabel(String type) {
        String last = type.substring(type.lastIndexOf('.') + 1);
        return last.replace('_', '-').toLowerCase();
    }

    /** Flattens a record body to {@code body...} field paths for field-by-field comparison. */
    public static Map<String, Object> flatten(String prefix, Object value) {
        Map<String, Object> out = new LinkedHashMap<>();
        switch (value) {
            case Map<?, ?> map ->
                map.forEach((key, entry) -> out.putAll(flatten(prefix + "." + key, entry)));
            case List<?> list -> {
                for (int i = 0; i < list.size(); i++) {
                    out.putAll(flatten(prefix + "[" + i + "]", list.get(i)));
                }
            }
            case null, default -> out.put(prefix, value);
        }
        return out;
    }
}
