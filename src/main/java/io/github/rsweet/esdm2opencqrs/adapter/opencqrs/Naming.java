package io.github.rsweet.esdm2opencqrs.adapter.opencqrs;

import io.github.rsweet.esdm2opencqrs.support.Str;

/** Java package and type names derived from kebab-case ESDM identifiers. */
public final class Naming {

    private Naming() {}

    /** A single Java package segment: {@code deleted-tasks} becomes {@code deletedtasks}. */
    public static String packageSegment(String name) {
        return name.replace("-", "").replace("_", "").toLowerCase();
    }

    public static String typeName(String name) {
        return Str.studly(name);
    }

    public static String memberName(String name) {
        return Str.camel(name);
    }

    /** Mongo collection name for a read model: {@code deleted-tasks} becomes {@code rm_deleted_tasks}. */
    public static String collection(String readModelName) {
        return "rm_" + Str.snake(readModelName);
    }

    public static String sourcePath(String packageName, String typeName) {
        return "src/main/java/" + packageName.replace('.', '/') + "/" + typeName + ".java";
    }

    public static String testSourcePath(String packageName, String typeName) {
        return "src/test/java/" + packageName.replace('.', '/') + "/" + typeName + ".java";
    }
}
