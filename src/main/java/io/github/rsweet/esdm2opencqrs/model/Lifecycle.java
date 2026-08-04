package io.github.rsweet.esdm2opencqrs.model;

import java.util.List;

/**
 * Aggregate-lifecycle role of a command/event. ESDM is descriptive and does not encode this, so it
 * is derived from an {@code esdm-extensions.io/lifecycle} annotation, falling back to a verb
 * heuristic on the document name.
 */
public enum Lifecycle {
    CREATE,
    MUTATE,
    DELETE;

    private static final List<String> CREATE_VERBS = List.of(
            "add", "create", "register", "open", "start", "new", "init", "submit", "draft", "place", "raise", "issue",
            "request");

    private static final List<String> DELETE_VERBS =
            List.of("delete", "remove", "archive", "close", "cancel", "discard", "withdraw");

    public static Lifecycle fromName(String name, String annotation) {
        if (annotation != null) {
            return switch (annotation) {
                case "create" -> CREATE;
                case "mutate" -> MUTATE;
                case "delete" -> DELETE;
                default -> throw new IllegalArgumentException("\"" + annotation + "\" is not a valid lifecycle");
            };
        }

        String verb = name.split("[-_]")[0];
        if (CREATE_VERBS.contains(verb)) {
            return CREATE;
        }
        if (DELETE_VERBS.contains(verb)) {
            return DELETE;
        }
        return MUTATE;
    }

    public String wireName() {
        return name().toLowerCase();
    }
}
