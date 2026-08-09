package io.github.rsweet.esdm2opencqrs.cli;

import io.github.rsweet.esdm2opencqrs.adapter.Adapter;
import io.github.rsweet.esdm2opencqrs.adapter.AdapterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(name = "targets", description = "List the available generation targets.")
public final class TargetsCommand implements Callable<Integer> {

    @CommandLine.Option(names = "--json", description = "Output as JSON (name, description, slug).")
    private boolean json;

    @Override
    public Integer call() {
        List<Adapter> adapters = AdapterRegistry.withDefaults().all();

        if (json) {
            System.out.println(toJson(adapters));
            return 0;
        }

        System.out.println();
        for (Adapter adapter : adapters) {
            System.out.println("  " + adapter.name());
            System.out.println("      " + adapter.description());
            System.out.println("      output subdirectory: " + adapter.slug() + "/");
            System.out.println();
        }
        return 0;
    }

    /** The registry as the array of {@code {name, description, slug}} objects the siblings emit. */
    static String toJson(List<Adapter> adapters) {
        List<String> entries = new ArrayList<>();
        for (Adapter adapter : adapters) {
            entries.add("{\"name\":" + quote(adapter.name())
                    + ",\"description\":" + quote(adapter.description())
                    + ",\"slug\":" + quote(adapter.slug()) + "}");
        }
        return "[" + String.join(",", entries) + "]";
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
