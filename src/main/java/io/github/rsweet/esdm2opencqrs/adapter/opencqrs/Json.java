package io.github.rsweet.esdm2opencqrs.adapter.opencqrs;

import java.util.List;
import java.util.Map;

/** Minimal JSON writer for the emitted 0004 catalog - no dependency, stable key order. */
public final class Json {

    private Json() {}

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        render(value, out, 0);
        return out.append('\n').toString();
    }

    private static void render(Object value, StringBuilder out, int depth) {
        switch (value) {
            case null -> out.append("null");
            case Map<?, ?> map -> renderMap(map, out, depth);
            case List<?> list -> renderList(list, out, depth);
            case String string -> out.append(quote(string));
            case Boolean bool -> out.append(bool);
            case Number number -> out.append(number);
            default -> out.append(quote(String.valueOf(value)));
        }
    }

    private static void renderMap(Map<?, ?> map, StringBuilder out, int depth) {
        if (map.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append("{\n");
        int index = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            indent(out, depth + 1);
            out.append(quote(String.valueOf(entry.getKey()))).append(": ");
            render(entry.getValue(), out, depth + 1);
            out.append(++index < map.size() ? ",\n" : "\n");
        }
        indent(out, depth);
        out.append('}');
    }

    private static void renderList(List<?> list, StringBuilder out, int depth) {
        if (list.isEmpty()) {
            out.append("[]");
            return;
        }
        out.append("[\n");
        for (int i = 0; i < list.size(); i++) {
            indent(out, depth + 1);
            render(list.get(i), out, depth + 1);
            out.append(i + 1 < list.size() ? ",\n" : "\n");
        }
        indent(out, depth);
        out.append(']');
    }

    private static void indent(StringBuilder out, int depth) {
        out.append("  ".repeat(depth));
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
