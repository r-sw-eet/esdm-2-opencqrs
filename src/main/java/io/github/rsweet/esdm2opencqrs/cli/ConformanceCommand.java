package io.github.rsweet.esdm2opencqrs.cli;

import io.github.rsweet.esdm2opencqrs.adapter.AdapterRegistry;
import io.github.rsweet.esdm2opencqrs.conformance.ConformanceRunner;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;

/** {@code conformance <app>...} - run the C4 cross-generator scenarios against this repo's target. */
@CommandLine.Command(
        name = "conformance",
        description = "Run C4 conformance scenarios against the generated app.")
public final class ConformanceCommand implements Callable<Integer> {

    @CommandLine.Parameters(arity = "1..*", description = "Scenario names, e.g. todo orders.")
    private List<String> apps;

    @CommandLine.Option(names = {"-t", "--target"}, description = "Target adapter id.")
    private String target = "opencqrs-eventsourcingdb";

    @CommandLine.Option(names = "--extensions", description = "Path to the esdm-extensions checkout.")
    private String extensions = "../esdm-extensions";

    @CommandLine.Option(names = "--port", description = "Host port for the api service (opencqrs owns 1814x).")
    private int port = 18140;

    @CommandLine.Option(names = "--keep", description = "Leave the compose stack running after the run.")
    private boolean keep;

    @Override
    public Integer call() throws Exception {
        int failures = 0;
        for (String app : apps) {
            failures += new ConformanceRunner(AdapterRegistry.withDefaults().get(target), Path.of(extensions), port, keep)
                    .run(app);
        }
        return failures == 0 ? 0 : 1;
    }
}
