package io.github.rsweet.esdm2opencqrs.adapter;

import io.github.rsweet.esdm2opencqrs.adapter.opencqrs.OpenCqrsAdapter;
import io.github.rsweet.esdm2opencqrs.adapter.opencqrs.OpenCqrsPostgresAdapter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AdapterRegistry {

    private final Map<String, Adapter> adapters = new LinkedHashMap<>();

    public static AdapterRegistry withDefaults() {
        AdapterRegistry registry = new AdapterRegistry();
        registry.register(new OpenCqrsAdapter());
        registry.register(new OpenCqrsPostgresAdapter());
        return registry;
    }

    public void register(Adapter adapter) {
        adapters.put(adapter.name(), adapter);
    }

    public Adapter get(String name) {
        Adapter adapter = adapters.get(name);
        if (adapter == null) {
            throw new IllegalArgumentException(
                    "Unknown target \"" + name + "\". Available: " + String.join(", ", adapters.keySet()));
        }
        return adapter;
    }

    public List<Adapter> all() {
        return List.copyOf(adapters.values());
    }
}
