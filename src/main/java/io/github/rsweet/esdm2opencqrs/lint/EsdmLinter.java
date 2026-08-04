package io.github.rsweet.esdm2opencqrs.lint;

import io.github.rsweet.esdm2opencqrs.model.Raw;
import io.github.rsweet.esdm2opencqrs.model.Yamls;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Validates an ESDM model against the canonical schema by shelling out to the upstream {@code esdm}
 * CLI. The generator's own parser is intentionally lax; this is the gate that keeps an invalid model
 * from reaching code generation.
 */
public final class EsdmLinter {

    private final String configuredBinary;
    private String resolved;
    private boolean resolutionAttempted;

    public EsdmLinter() {
        this(null);
    }

    public EsdmLinter(String binary) {
        this.configuredBinary = binary;
    }

    public boolean isAvailable() {
        return resolveBinary() != null;
    }

    /** Resolved path to the {@code esdm} binary, or a hint of where it was looked for. */
    public String binaryHint() {
        String binary = resolveBinary();
        return binary == null ? "esdm (not found on PATH, ESDM_BIN, or tools/esdm)" : binary;
    }

    public LintResult lint(Path modelDirectory) throws IOException, InterruptedException {
        String binary = resolveBinary();
        if (binary == null) {
            throw new IllegalStateException("esdm binary not found. Install it "
                    + "(https://www.esdm.io/getting-started/installing-esdm/), put it at tools/esdm, "
                    + "or set the ESDM_BIN environment variable.");
        }

        Process process = new ProcessBuilder(
                        binary, "lint", "-d", modelDirectory.toString(), "--format", "json", "--color", "never")
                .start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor(2, TimeUnit.MINUTES);

        Object decoded;
        try {
            String payload = stdout.isBlank() ? "[]" : stdout;
            decoded = Yamls.newYaml().load(payload);
        } catch (RuntimeException e) {
            decoded = null;
        }
        if (!(decoded instanceof List<?> entries)) {
            throw new IllegalStateException("esdm lint did not return parseable JSON (exit " + process.exitValue()
                    + "): " + (stderr.isBlank() ? stdout.trim() : stderr.trim()));
        }

        List<LintFinding> findings = new ArrayList<>();
        for (Object entry : entries) {
            if (entry instanceof Map<?, ?>) {
                Map<String, Object> raw = Raw.record(entry);
                findings.add(LintFinding.fromRaw(raw));
            }
        }

        return new LintResult(List.copyOf(findings));
    }

    private String resolveBinary() {
        if (resolutionAttempted) {
            return resolved;
        }
        resolutionAttempted = true;

        for (String candidate : candidates()) {
            if (candidate != null && isExecutableFile(Paths.get(candidate))) {
                resolved = candidate;
                return resolved;
            }
        }

        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String directory : pathEnv.split(":")) {
                if (directory.isEmpty()) {
                    continue;
                }
                Path candidate = Paths.get(directory, "esdm");
                if (isExecutableFile(candidate)) {
                    resolved = candidate.toString();
                    return resolved;
                }
            }
        }

        return null;
    }

    private List<String> candidates() {
        List<String> candidates = new ArrayList<>();
        candidates.add(configuredBinary);
        String env = System.getenv("ESDM_BIN");
        candidates.add(env == null || env.isEmpty() ? null : env);
        candidates.add(Paths.get("tools", "esdm").toAbsolutePath().toString());
        return candidates;
    }

    private static boolean isExecutableFile(Path path) {
        return Files.isRegularFile(path) && Files.isExecutable(path);
    }
}
