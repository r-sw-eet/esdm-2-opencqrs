package app.todo.support;

import java.util.LinkedHashMap;
import java.util.Map;

/** The family's error body: {@code { error, message, details }}. */
public record ApiError(String error, String message, Map<String, Object> details) {

    public static Map<String, Object> details(String... keysAndValues) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            details.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return details;
    }
}
