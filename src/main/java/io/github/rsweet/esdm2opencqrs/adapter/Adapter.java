package io.github.rsweet.esdm2opencqrs.adapter;

import io.github.rsweet.esdm2opencqrs.model.Model;
import java.util.Map;

/**
 * A generation target: one framework + database + event-sourcing library combo. Adapters are the
 * only place that knows about a concrete stack; everything upstream is framework-agnostic.
 */
public interface Adapter {

    /** Stable target id selected on the CLI with {@code --target}. */
    String name();

    String description();

    /** Short stack slug - the subdirectory each target writes into under {@code generated/}. */
    String slug();

    GeneratedProject generate(Model model, Map<String, Object> options);
}
