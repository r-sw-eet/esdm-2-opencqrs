package io.github.rsweet.esdm2opencqrs.cli;

import io.github.rsweet.esdm2opencqrs.adapter.Adapter;
import io.github.rsweet.esdm2opencqrs.adapter.AdapterRegistry;
import io.github.rsweet.esdm2opencqrs.adapter.GeneratedProject;
import io.github.rsweet.esdm2opencqrs.feel.Feel;
import io.github.rsweet.esdm2opencqrs.feel.FeelException;
import io.github.rsweet.esdm2opencqrs.feel.FeelNode;
import io.github.rsweet.esdm2opencqrs.feel.Mapping;
import io.github.rsweet.esdm2opencqrs.lint.EsdmLinter;
import io.github.rsweet.esdm2opencqrs.lint.LintFinding;
import io.github.rsweet.esdm2opencqrs.lint.LintResult;
import io.github.rsweet.esdm2opencqrs.model.Aggregate;
import io.github.rsweet.esdm2opencqrs.model.Command;
import io.github.rsweet.esdm2opencqrs.model.DocumentLoader;
import io.github.rsweet.esdm2opencqrs.model.Feature;
import io.github.rsweet.esdm2opencqrs.model.Field;
import io.github.rsweet.esdm2opencqrs.model.Event;
import io.github.rsweet.esdm2opencqrs.model.Model;
import io.github.rsweet.esdm2opencqrs.model.ModelFactory;
import io.github.rsweet.esdm2opencqrs.model.Policy;
import io.github.rsweet.esdm2opencqrs.model.Raw;
import io.github.rsweet.esdm2opencqrs.model.StateMachine;
import io.github.rsweet.esdm2opencqrs.model.Yamls;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import picocli.CommandLine;

/**
 * {@code generate <app-dir>} - read the app's {@code esdmgen.yaml}, parse its ESDM model and emit a
 * project with the chosen target adapter.
 */
@CommandLine.Command(name = "generate", description = "Generate an application from an ESDM model.")
public final class GenerateCommand implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", arity = "0..1", description = "The app directory holding esdmgen.yaml.")
    private String appDirectory = ".";

    @CommandLine.Option(names = {"-t", "--target"}, description = "Target adapter id.")
    private String target;

    @CommandLine.Option(names = {"-m", "--model"}, description = "Model directory, relative to the app directory.")
    private String modelOption;

    @CommandLine.Option(names = {"-o", "--out"}, description = "Output directory, relative to the app directory.")
    private String outOption;

    @CommandLine.Option(names = "--skip-lint", description = "Skip the esdm lint gate and the FEEL/GWT gates.")
    private boolean skipLint;

    @CommandLine.Option(names = "--strict", description = "Treat lint warnings as errors.")
    private boolean strict;

    @Override
    public Integer call() throws Exception {
        Path appDir = Paths.get(appDirectory).normalize();
        Map<String, Object> config = readConfig(appDir.resolve("esdmgen.yaml"));

        String targetName = target != null ? target : Raw.string(config.get("target"), "");
        Path modelDir = appDir.resolve(modelOption != null ? modelOption : Raw.string(config.get("model"), "model"));
        Path outDir = appDir.resolve(outOption != null ? outOption : Raw.string(config.get("out"), "generated"));
        Map<String, Object> options = Raw.record(config.get("options"));

        if (targetName.isEmpty()) {
            error("No target adapter given (set `target:` in esdmgen.yaml or pass --target).");
            return 1;
        }

        boolean strictLint = strict || Raw.bool(Raw.record(config.get("lint")).getOrDefault("strict", false));
        if (!skipLint && !lint(modelDir, strictLint)) {
            return 1;
        }

        System.out.println("\nGenerating \"" + targetName + "\" from " + modelDir + "\n");

        Model model = ModelFactory.create(DocumentLoader.loadDirectory(modelDir));

        if (!skipLint && !validateModelFeel(model)) {
            return 1;
        }
        if (!skipLint && !validateFeatureScenarios(model)) {
            return 1;
        }
        if (!skipLint && !validateReactionMappings(model)) {
            return 1;
        }

        Adapter adapter;
        try {
            adapter = AdapterRegistry.withDefaults().get(targetName);
        } catch (IllegalArgumentException e) {
            error(e.getMessage());
            return 1;
        }

        // Each stack writes into its own subdir so multiple targets never collide.
        Path targetDir = outDir.resolve(adapter.slug());

        // Embed the app's BPMN (if any) so a console Author tab can load it.
        options.put("bpmnSource", readBpmnSource(appDir));

        GeneratedProject project = adapter.generate(model, options);
        project.writeTo(targetDir);

        project.files().keySet().forEach(path -> System.out.println(" * " + path));
        System.out.println("\n[OK] Wrote " + project.files().size() + " files to " + targetDir);

        return 0;
    }

    private static Map<String, Object> readConfig(Path configPath) {
        if (!Files.isRegularFile(configPath)) {
            return new LinkedHashMap<>();
        }
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            return Raw.record(Yamls.newYaml().load(reader));
        } catch (IOException e) {
            return new LinkedHashMap<>();
        }
    }

    /** The first BPMN file under the app's {@code authoring/} directory, if present. */
    private static String readBpmnSource(Path appDir) {
        Path authoring = appDir.resolve("authoring");
        if (!Files.isDirectory(authoring)) {
            return "";
        }
        try (Stream<Path> files = Files.list(authoring)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".bpmn"))
                    .min(Comparator.comparing(Path::toString))
                    .map(path -> {
                        try {
                            return Files.readString(path, StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            return "";
                        }
                    })
                    .orElse("");
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Run {@code esdm lint} as a gate before generation. An invalid model never reaches the adapter -
     * garbage in would only mean garbage out.
     */
    private static boolean lint(Path modelDir, boolean strict) {
        EsdmLinter linter = new EsdmLinter();
        if (!linter.isAvailable()) {
            error("Cannot validate the model: " + linter.binaryHint());
            return false;
        }

        System.out.println("\nLinting model in " + modelDir + "\n");

        LintResult result;
        try {
            result = linter.lint(modelDir);
        } catch (IOException | InterruptedException | RuntimeException e) {
            error(e.getMessage());
            return false;
        }

        for (LintFinding finding : result.findings()) {
            String location = finding.locationLabel().isEmpty() ? "" : " (" + finding.locationLabel() + ")";
            System.out.println("  " + (finding.isError() ? "error" : "warning") + " " + finding.message() + location
                    + " [" + finding.ruleId() + "]");
        }

        if (result.hasErrors()) {
            error("Model is not valid ESDM - aborting before generation.");
            return false;
        }
        if (strict && !result.warnings().isEmpty()) {
            error("Lint warnings present and --strict is set - aborting.");
            return false;
        }
        if (result.isClean()) {
            System.out.println("Model passes esdm lint cleanly.");
        }

        return true;
    }

    /**
     * Model-aware FEEL gate (proposal 0002): parse every state-machine guard expression and bind its
     * identifiers to real aggregate fields. Complements the structural {@code esdm lint}.
     */
    private static boolean validateModelFeel(Model model) {
        List<String> errors = new ArrayList<>();
        for (Aggregate aggregate : model.aggregates()) {
            StateMachine machine = aggregate.stateMachine();
            if (machine == null) {
                continue;
            }
            List<String> allowed = new ArrayList<>(
                    aggregate.state().fields().stream().map(Field::name).toList());
            allowed.add("status");

            // The arithmetic gate needs the declared types, which the binder never had.
            Map<String, String> types = new LinkedHashMap<>();
            aggregate.state().fields().forEach(field -> types.put(field.name(), field.jsonType()));

            for (StateMachine.Admit admit : machine.admits()) {
                if (admit.when() == null || admit.when().isEmpty()) {
                    continue;
                }
                try {
                    for (String bindError : Feel.validate(Feel.parse(admit.when()), allowed, types)) {
                        errors.add(admit.command() + ".when \"" + admit.when() + "\": " + bindError);
                    }
                } catch (FeelException e) {
                    errors.add(admit.command() + ".when \"" + admit.when() + "\": " + e.getMessage());
                }
            }
        }

        if (errors.isEmpty()) {
            return true;
        }

        System.out.println("\nFEEL validation\n");
        errors.forEach(e -> System.out.println("  error " + e));
        error("FEEL guard expressions are invalid - aborting before generation.");
        return false;
    }

    /**
     * GWT consistency gate: a command emits exactly the events it {@code publishes}, so a scenario's
     * {@code then} events must all be published by the triggering command. A cascade belongs in a
     * {@code policy} (event -&gt; command), not in the command's own outcome.
     */
    private static boolean validateFeatureScenarios(Model model) {
        List<String> errors = new ArrayList<>();
        for (Feature feature : model.features()) {
            Aggregate aggregate = model.aggregate(feature.boundedContext(), feature.aggregate());
            if (aggregate == null) {
                continue;
            }
            for (Feature.Scenario scenario : feature.scenarios()) {
                if (scenario.isRejection()) {
                    continue;
                }
                Command command = aggregate.command(scenario.commandName());
                if (command == null) {
                    continue;
                }
                for (Feature.EventExample example : scenario.thenEvents()) {
                    if (!command.publishes().contains(example.event())) {
                        String published =
                                command.publishes().isEmpty() ? "nothing" : String.join(", ", command.publishes());
                        errors.add(feature.name() + "/" + scenario.name() + ": then-event \"" + example.event()
                                + "\" is not published by \"" + command.name() + "\" (publishes: " + published
                                + "). A command emits only the events it publishes; model a cascade as a policy"
                                + " (event -> command).");
                    }
                }
            }
        }

        if (errors.isEmpty()) {
            return true;
        }

        System.out.println("\nFeature scenario validation\n");
        errors.forEach(e -> System.out.println("  error " + e));
        error("Feature scenarios declare events their command does not publish - aborting before generation.");
        return false;
    }

    /**
     * Reaction mapping gate (proposal 0005): a policy's {@code esdm-extensions.io/mapping} must
     * assign only fields the emitted command declares, must produce every required one, and its
     * expressions bind against the handled event's payload.
     */
    private static boolean validateReactionMappings(Model model) {
        List<String> errors = new ArrayList<>();
        for (Policy policy : model.policies()) {
            if (policy.mapping().isEmpty()) {
                continue;
            }
            Aggregate handled = model.aggregate(policy.handleContext(), policy.handleAggregate());
            Aggregate emitting = model.aggregate(policy.emitContext(), policy.emitAggregate());
            if (handled == null || emitting == null) {
                continue;
            }
            Event event = handled.event(policy.handleEvent());
            Command command = emitting.command(policy.emitCommand());
            if (event == null || command == null) {
                continue;
            }

            Map<String, FeelNode> mapping;
            try {
                mapping = Mapping.parse(policy.mapping());
            } catch (FeelException e) {
                errors.add(policy.name() + ": " + e.getMessage());
                continue;
            }

            List<String> declared = command.data().fields().stream().map(Field::name).toList();
            for (String key : mapping.keySet()) {
                if (!declared.contains(key) && !key.equals(emitting.identityField())) {
                    errors.add(policy.name() + ": \"" + key + "\" is not a field of command \"" + command.name()
                            + "\" (declared: " + (declared.isEmpty() ? "nothing" : String.join(", ", declared)) + ")");
                }
            }
            for (Field field : command.data().fields()) {
                if (field.required() && !mapping.containsKey(field.name())) {
                    errors.add(policy.name() + ": required field \"" + field.name() + "\" of command \""
                            + command.name() + "\" is not assigned by the mapping");
                }
            }

            List<String> bindable =
                    new ArrayList<>(event.data().fields().stream().map(Field::name).toList());
            errors.addAll(Mapping.validate(mapping, bindable).stream()
                    .map(error -> policy.name() + ": " + error)
                    .toList());
        }

        if (errors.isEmpty()) {
            return true;
        }

        System.out.println("\nReaction mapping validation\n");
        errors.forEach(e -> System.out.println("  error " + e));
        error("Reaction mappings are invalid - aborting before generation.");
        return false;
    }

    private static void error(String message) {
        System.err.println("\n[ERROR] " + message);
    }
}
