package io.github.rsweet.esdm2opencqrs.cli;

import io.github.rsweet.esdm2opencqrs.adapter.Adapter;
import io.github.rsweet.esdm2opencqrs.adapter.AdapterRegistry;
import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(name = "targets", description = "List the available generation targets.")
public final class TargetsCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println();
        for (Adapter adapter : AdapterRegistry.withDefaults().all()) {
            System.out.println("  " + adapter.name());
            System.out.println("      " + adapter.description());
            System.out.println("      output subdirectory: " + adapter.slug() + "/");
            System.out.println();
        }
        return 0;
    }
}
