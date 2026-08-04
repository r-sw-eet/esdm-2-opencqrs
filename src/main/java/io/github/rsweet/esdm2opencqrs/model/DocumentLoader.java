package io.github.rsweet.esdm2opencqrs.model;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.yaml.snakeyaml.Yaml;

/** Loads ESDM YAML files (one or many documents per file, separated by {@code ---}) into raw maps. */
public final class DocumentLoader {

    private DocumentLoader() {}

    public static List<Map<String, Object>> loadDirectory(Path directory) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Model directory \"" + directory + "\" does not exist.");
        }

        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(directory)) {
            walk.filter(Files::isRegularFile).filter(DocumentLoader::isYaml).forEach(files::add);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read model directory " + directory, e);
        }
        files.sort(Comparator.comparing(Path::toString));

        List<Map<String, Object>> documents = new ArrayList<>();
        Yaml yaml = Yamls.newYaml();
        for (Path file : files) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                for (Object loaded : yaml.loadAll(reader)) {
                    if (loaded instanceof Map<?, ?> map && !map.isEmpty()) {
                        documents.add(Raw.record(map));
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("failed to read " + file, e);
            }
        }

        return documents;
    }

    private static boolean isYaml(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".yaml") || name.endsWith(".yml");
    }
}
