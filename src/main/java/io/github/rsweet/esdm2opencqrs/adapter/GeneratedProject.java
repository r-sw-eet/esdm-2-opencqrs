package io.github.rsweet.esdm2opencqrs.adapter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** An in-memory tree of files an adapter wants written, keyed by relative path. */
public final class GeneratedProject {

    private final Map<String, String> files = new LinkedHashMap<>();

    public void add(String relativePath, String contents) {
        files.put(relativePath.replaceAll("^/+", ""), contents);
    }

    public Map<String, String> files() {
        return Collections.unmodifiableMap(files);
    }

    public void writeTo(Path directory) throws IOException {
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Path target = directory.resolve(entry.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, entry.getValue(), StandardCharsets.UTF_8);
        }
    }
}
