package io.github.rsweet.esdm2opencqrs.conformance;

import io.github.rsweet.esdm2opencqrs.adapter.Adapter;
import io.github.rsweet.esdm2opencqrs.adapter.GeneratedProject;
import io.github.rsweet.esdm2opencqrs.model.DocumentLoader;
import io.github.rsweet.esdm2opencqrs.model.Model;
import io.github.rsweet.esdm2opencqrs.model.ModelFactory;
import io.github.rsweet.esdm2opencqrs.model.Raw;
import io.github.rsweet.esdm2opencqrs.model.Yamls;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The native C4 runner (conformance README, "Runner contract"): generate this repo's target from the
 * canonical model, boot the emitted compose stack, execute the scenario, normalize the observations
 * and compare them against the recorded golden answers.
 */
public final class ConformanceRunner {

    private static final Duration BOOT_TIMEOUT = Duration.ofSeconds(300);
    private static final Duration CONVERGE_TIMEOUT = Duration.ofSeconds(90);
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(45);

    private final Adapter adapter;
    private final Path extensionsDir;
    private final int port;
    private final boolean keepStack;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public ConformanceRunner(Adapter adapter, Path extensionsDir, int port, boolean keepStack) {
        this.adapter = adapter;
        this.extensionsDir = extensionsDir;
        this.port = port;
        this.keepStack = keepStack;
    }

    public int run(String app) throws Exception {
        Path conformance = extensionsDir.resolve("conformance");
        Path scenarioPath = conformance.resolve("scenarios/" + app + ".yaml");
        if (!Files.isRegularFile(scenarioPath)) {
            System.err.println("[ERROR] No such scenario: " + scenarioPath);
            return 1;
        }

        Map<String, Object> scenario = Raw.record(Yamls.newYaml().load(Files.readString(scenarioPath)));
        String target = adapter.slug();
        List<Object> targets = Raw.list(scenario.get("targets"));
        if (!targets.contains(target)) {
            System.out.println("skip: scenario \"" + app + "\" does not list target \"" + target + "\"");
            return 0;
        }

        // The scenario's model path is workspace-relative and canonical - never the local copy.
        Path workspace = extensionsDir.toAbsolutePath().normalize().getParent();
        Path modelDir = workspace.resolve(Raw.string(scenario.get("model"), ""));
        if (!Files.isDirectory(modelDir)) {
            System.err.println("[ERROR] Canonical model not found: " + modelDir);
            return 1;
        }

        Path stackDir = Files.createTempDirectory("esdm-c4-" + app + "-");
        System.out.println("\nC4 " + app + " [" + target + "] on port " + port);
        System.out.println("  model  " + modelDir);
        System.out.println("  stack  " + stackDir);

        Model model = ModelFactory.create(DocumentLoader.loadDirectory(modelDir));
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("appName", app);
        options.put("basePackage", "app." + app.replace("-", ""));
        GeneratedProject project = adapter.generate(model, options);
        project.writeTo(stackDir);
        Files.writeString(
                stackDir.resolve("compose.override.yaml"),
                composeOverride(project.files().get("compose.yaml")),
                StandardCharsets.UTF_8);

        overlayLocalArtifacts(stackDir);

        String projectName = "c4-" + target + "-" + app;
        try {
            compose(stackDir, projectName, "up", "-d", "--build");
            if (!waitForCatalog()) {
                System.err.println("[ERROR] api did not become ready within " + BOOT_TIMEOUT.toSeconds() + "s");
                dumpLogs(stackDir, projectName);
                return 1;
            }

            Map<String, String> captures = new LinkedHashMap<>();
            List<Map<String, Object>> records = new ArrayList<>();
            records.addAll(runSteps(Raw.list(scenario.get("steps")), captures));
            records.addAll(runCheckpoints(Raw.list(scenario.get("checkpoints")), captures));

            Map<String, Object> golden = Raw.record(
                    Yamls.newYaml().load(Files.readString(conformance.resolve("golden/" + app + ".observations.json"))));
            Map<String, Object> registry = Raw.record(
                    Yamls.newYaml().load(Files.readString(conformance.resolve("registry.yaml"))));

            return new Differ(registry, target).report(normalize(records, captures), golden);
        } finally {
            if (keepStack) {
                System.out.println("  (stack left running: docker compose -p " + projectName + " down -v)");
            } else {
                compose(stackDir, projectName, "down", "-v");
            }
        }
    }

    /**
     * Publish only the api service, on a host port that cannot collide with dev stacks or other
     * runners. The store services differ per target, so they are read back off the emitted compose.
     */
    private String composeOverride(String compose) {
        StringBuilder override = new StringBuilder("services:\n");
        for (String service : serviceNames(compose)) {
            if (!service.equals("api")) {
                override.append("  ").append(service).append(":\n    ports: !reset []\n");
            }
        }
        return override.append("  api:\n    ports:\n      - \"")
                .append(port)
                .append(":8080\"\n")
                .toString();
    }

    /**
     * A target may depend on an OpenCQRS build that is not published yet (the PostgreSQL prototype
     * does). Its emitted Dockerfile overlays {@code .m2-overlay/} onto the builder's local
     * repository; fill it from this machine's, so the image can resolve those artifacts.
     */
    private static void overlayLocalArtifacts(Path stackDir) throws IOException {
        Path overlay = stackDir.resolve(".m2-overlay");
        if (!Files.isDirectory(overlay)) {
            return;
        }
        Path source = Path.of(System.getProperty("user.home"), ".m2", "repository", "com", "opencqrs");
        if (!Files.isDirectory(source)) {
            return;
        }

        Path destination = overlay.resolve("com/opencqrs");
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path target = destination.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        System.out.println("  overlay " + destination);
    }

    private static List<String> serviceNames(String compose) {
        Map<String, Object> parsed = Raw.record(Yamls.newYaml().load(compose));
        return List.copyOf(Raw.record(parsed.get("services")).keySet());
    }

    private List<Map<String, Object>> runSteps(List<Object> steps, Map<String, String> captures) throws Exception {
        List<Map<String, Object>> records = new ArrayList<>();
        for (Object rawStep : steps) {
            Map<String, Object> step = Raw.record(rawStep);
            String name = Raw.string(step.get("name"), "");

            if (step.containsKey("post")) {
                String path = resolve(Raw.string(step.get("post"), ""), captures);
                Object body = resolvePlaceholders(Raw.record(step.get("body")), captures);
                HttpResponse<String> response = post(path, Observations.canonical(body));
                Object parsed = parse(response.body());

                if (step.containsKey("capture") && parsed instanceof Map<?, ?> map) {
                    Object id = map.get("id");
                    if (id instanceof String value) {
                        captures.put(Raw.string(step.get("capture"), ""), value);
                    }
                }
                records.add(record("step", name, "POST " + Raw.string(step.get("post"), ""), response.statusCode(), parsed));
            } else if (step.containsKey("get")) {
                String raw = Raw.string(step.get("get"), "");
                String path = resolve(raw, captures);
                HttpResponse<String> response = get(path);
                Object parsed = parse(response.body());

                // A policy fires asynchronously, so a step may have to wait for its rows to land.
                if (Raw.bool(step.getOrDefault("poll", false))) {
                    long minRows = Long.parseLong(Raw.string(step.get("min_rows"), "1"));
                    long timeout = Long.parseLong(
                            Raw.string(step.get("poll_timeout"), String.valueOf(POLL_TIMEOUT.toSeconds())));
                    long deadline = System.nanoTime() + Duration.ofSeconds(timeout).toNanos();
                    while (rowCount(parsed) < minRows && System.nanoTime() < deadline) {
                        TimeUnit.SECONDS.sleep(1);
                        response = get(path);
                        parsed = parse(response.body());
                    }
                }

                if (step.containsKey("capture")) {
                    captureFromRows(step, parsed, captures);
                }
                records.add(record("step", name, "GET " + raw, response.statusCode(), parsed));
            }
        }
        return records;
    }

    private static int rowCount(Object parsed) {
        return parsed instanceof List<?> rows ? rows.size() : 0;
    }

    private void captureFromRows(Map<String, Object> step, Object parsed, Map<String, String> captures) {
        if (!(parsed instanceof List<?> rows)) {
            return;
        }
        String field = Raw.string(step.get("capture_field"), "id");
        List<Object> sorted = new ArrayList<>(rows);
        sorted.sort((left, right) -> Observations.canonical(left).compareTo(Observations.canonical(right)));
        for (Object row : sorted) {
            Object value = Raw.record(row).get(field);
            if (value instanceof String candidate && !captures.containsValue(candidate)) {
                captures.put(Raw.string(step.get("capture"), ""), candidate);
                return;
            }
        }
    }

    /** Poll every checkpoint until two consecutive identical reads, then record it. */
    private List<Map<String, Object>> runCheckpoints(List<Object> checkpoints, Map<String, String> captures)
            throws Exception {
        List<Map<String, Object>> records = new ArrayList<>();
        for (Object rawCheckpoint : checkpoints) {
            Map<String, Object> checkpoint = Raw.record(rawCheckpoint);
            String name = Raw.string(checkpoint.get("name"), "");
            String raw = Raw.string(checkpoint.get("get"), "");
            String path = resolve(raw, captures);

            long deadline = System.nanoTime() + CONVERGE_TIMEOUT.toNanos();
            String previous = null;
            HttpResponse<String> response = get(path);
            while (System.nanoTime() < deadline) {
                String current = response.statusCode() + "|" + Observations.canonical(parse(response.body()));
                if (current.equals(previous)) {
                    break;
                }
                previous = current;
                TimeUnit.SECONDS.sleep(1);
                response = get(path);
            }

            records.add(record("checkpoint", name, "GET " + raw, response.statusCode(), parse(response.body())));
        }
        return records;
    }

    /**
     * Normalization runs once at the end: masking needs the complete capture set, and the golden
     * files are byte-comparisons against exactly this shape.
     */
    private List<Map<String, Object>> normalize(List<Map<String, Object>> records, Map<String, String> captures) {
        List<Map<String, Object>> out = new ArrayList<>(records.size());
        for (Map<String, Object> record : records) {
            Map<String, Object> normalized = new LinkedHashMap<>(record);
            Object body = record.get("body");
            normalized.put(
                    "body",
                    "events".equals(record.get("checkpoint"))
                            ? Observations.normalizeEventRows(body, captures)
                            : Observations.sortIfList(Observations.camelizeKeys(Observations.mask(body, captures))));
            out.add(normalized);
        }
        return out;
    }

    private Map<String, Object> record(String kind, String name, String endpoint, int status, Object body) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put(kind, name);
        record.put("endpoint", endpoint);
        record.put("status", status);
        record.put("body", body);
        return record;
    }

    // ---- http --------------------------------------------------------------

    private HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
        return http.send(
                HttpRequest.newBuilder(URI.create(base() + "/" + path))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(30))
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return http.send(
                HttpRequest.newBuilder(URI.create(base() + "/" + path))
                        .timeout(Duration.ofSeconds(30))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String base() {
        return "http://localhost:" + port;
    }

    private boolean waitForCatalog() throws InterruptedException {
        long deadline = System.nanoTime() + BOOT_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                if (get("_dev/catalog").statusCode() == 200) {
                    return true;
                }
            } catch (IOException ignored) {
                // not up yet
            }
            TimeUnit.SECONDS.sleep(2);
        }
        return false;
    }

    // ---- placeholders ------------------------------------------------------

    private String resolve(String value, Map<String, String> captures) {
        String out = value;
        for (Map.Entry<String, String> capture : captures.entrySet()) {
            out = out.replace("$" + capture.getKey(), capture.getValue());
        }
        return out;
    }

    private Object resolvePlaceholders(Object value, Map<String, String> captures) {
        return switch (value) {
            case String string -> resolve(string, captures);
            case Map<?, ?> map -> {
                Map<String, Object> out = new LinkedHashMap<>();
                map.forEach((key, entry) -> out.put(String.valueOf(key), resolvePlaceholders(entry, captures)));
                yield out;
            }
            case List<?> list -> list.stream().map(item -> resolvePlaceholders(item, captures)).toList();
            case null, default -> value;
        };
    }

    private Object parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return Yamls.newYaml().load(body);
        } catch (RuntimeException e) {
            return body;
        }
    }

    // ---- docker ------------------------------------------------------------

    private void compose(Path directory, String projectName, String... arguments) throws Exception {
        List<String> command = new ArrayList<>(
                List.of("docker", "compose", "-p", projectName, "-f", "compose.yaml", "-f", "compose.override.yaml"));
        command.addAll(List.of(arguments));

        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(20, TimeUnit.MINUTES) || process.exitValue() != 0) {
            throw new IllegalStateException("docker compose " + String.join(" ", arguments) + " failed:\n" + output);
        }
    }

    private void dumpLogs(Path directory, String projectName) {
        try {
            compose(directory, projectName, "logs", "--tail", "60");
        } catch (Exception e) {
            System.err.println("  (could not read compose logs: " + e.getMessage() + ")");
        }
    }
}
