package io.github.rsweet.esdm2opencqrs.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Defensive accessors for the untyped YAML tree. */
public final class Raw {

    private Raw() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> record(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, entry) -> result.put(String.valueOf(key), entry));
            return result;
        }
        return new LinkedHashMap<>();
    }

    public static List<Object> list(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value == null) {
            return new ArrayList<>();
        }
        List<Object> single = new ArrayList<>();
        single.add(value);
        return single;
    }

    public static String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    public static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public static boolean bool(Object value) {
        return value instanceof Boolean flag ? flag : Boolean.parseBoolean(String.valueOf(value));
    }
}
