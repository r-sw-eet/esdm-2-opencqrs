package io.github.rsweet.esdm2opencqrs.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns raw ESDM documents into a resolved {@link Model}: groups by kind, builds typed nodes and
 * wires every cross-reference (command -&gt; event, event -&gt; aggregate, read-model -&gt; events,
 * query -&gt; read-model). This is the parse-and-map stage; it knows nothing about any target framework.
 */
public final class ModelFactory {

    private static final String LIFECYCLE_ANNOTATION = "esdm-extensions.io/lifecycle";

    private ModelFactory() {}

    public static Model create(List<Map<String, Object>> documents) {
        Map<String, List<Map<String, Object>>> byKind = new LinkedHashMap<>();
        for (Map<String, Object> document : documents) {
            byKind.computeIfAbsent(Raw.string(document.get("kind"), ""), k -> new ArrayList<>())
                    .add(document);
        }

        String domainName = singleDomainName(byKind.getOrDefault("domain", List.of()));

        Map<String, BoundedContext> contexts = new LinkedHashMap<>();
        for (Map<String, Object> document : byKind.getOrDefault("bounded-context", List.of())) {
            String name = Raw.string(document.get("name"), "");
            contexts.put(name, new BoundedContext(name, domainName));
        }

        Map<String, Aggregate> aggregateIndex = new LinkedHashMap<>();
        for (Map<String, Object> document : byKind.getOrDefault("aggregate", List.of())) {
            Map<String, Object> scope = Raw.record(document.get("scope"));
            String contextName = Raw.string(scope.get("boundedContext"), "default");
            String identityField = Raw.string(Raw.record(document.get("identifiedBy")).get("field"), "id");
            Schema state = Schema.fromRaw(Raw.record(document.get("state"))).withIdentity(identityField);

            Aggregate aggregate = new Aggregate(
                    Raw.string(document.get("name"), ""), domainName, contextName, identityField, state);

            context(contexts, contextName, domainName).aggregates().add(aggregate);
            aggregateIndex.put(contextName + "/" + aggregate.name(), aggregate);
        }

        // Commands first: they tell us which events are create/delete.
        List<Map<String, Object>> rawCommands = byKind.getOrDefault("command", List.of());
        Map<String, Lifecycle> eventLifecycle = new LinkedHashMap<>();
        for (Map<String, Object> document : rawCommands) {
            Lifecycle lifecycle = Lifecycle.fromName(
                    Raw.string(document.get("name"), ""), annotation(document, LIFECYCLE_ANNOTATION));
            for (Object eventName : Raw.list(document.get("publishes"))) {
                eventLifecycle.put(String.valueOf(eventName), lifecycle);
            }
        }

        for (Map<String, Object> document : byKind.getOrDefault("event", List.of())) {
            Map<String, Object> scope = Raw.record(document.get("scope"));
            String contextName = Raw.string(scope.get("boundedContext"), "default");
            String aggregateName = Raw.string(scope.get("aggregate"), "");
            Aggregate aggregate = aggregateIndex.get(contextName + "/" + aggregateName);
            if (aggregate == null) {
                continue;
            }

            String name = Raw.string(document.get("name"), "");
            String annotated = annotation(document, LIFECYCLE_ANNOTATION);
            Lifecycle lifecycle = annotated != null
                    ? Lifecycle.fromName(name, annotated)
                    : eventLifecycle.getOrDefault(name, Lifecycle.MUTATE);
            String type = annotation(document, "cloudevents.type");

            aggregate.events()
                    .add(new Event(
                            name,
                            domainName,
                            contextName,
                            aggregateName,
                            Schema.fromRaw(Raw.record(document.get("data"))).withIdentity(aggregate.identityField()),
                            lifecycle,
                            type != null ? type : domainName + "." + aggregateName + "." + name));
        }

        for (Map<String, Object> document : rawCommands) {
            Map<String, Object> scope = Raw.record(document.get("scope"));
            String contextName = Raw.string(scope.get("boundedContext"), "default");
            String aggregateName = Raw.string(scope.get("aggregate"), "");
            Aggregate aggregate = aggregateIndex.get(contextName + "/" + aggregateName);
            if (aggregate == null) {
                continue;
            }

            List<String> publishes =
                    Raw.list(document.get("publishes")).stream().map(String::valueOf).toList();

            aggregate.commands()
                    .add(new Command(
                            Raw.string(document.get("name"), ""),
                            domainName,
                            contextName,
                            aggregateName,
                            Schema.fromRaw(Raw.record(document.get("data"))),
                            publishes,
                            Lifecycle.fromName(
                                    Raw.string(document.get("name"), ""),
                                    annotation(document, LIFECYCLE_ANNOTATION))));
        }

        for (Map<String, Object> document : byKind.getOrDefault("state-machine", List.of())) {
            Map<String, Object> scope = Raw.record(document.get("scope"));
            String contextName = Raw.string(scope.get("boundedContext"), "default");
            String aggregateName = Raw.string(scope.get("aggregate"), "");
            Aggregate aggregate = aggregateIndex.get(contextName + "/" + aggregateName);
            if (aggregate == null) {
                continue;
            }

            List<StateMachine.State> states = Raw.list(document.get("states")).stream()
                    .map(Raw::record)
                    .map(raw -> new StateMachine.State(
                            Raw.string(raw.get("name"), ""), Raw.bool(raw.getOrDefault("final", false))))
                    .toList();
            List<StateMachine.Transition> transitions = Raw.list(document.get("transitions")).stream()
                    .map(Raw::record)
                    .map(raw -> new StateMachine.Transition(
                            Raw.string(raw.get("on"), ""), Raw.string(raw.get("to"), "")))
                    .toList();
            List<StateMachine.Admit> admits = Raw.list(document.get("admits")).stream()
                    .map(Raw::record)
                    .map(raw -> new StateMachine.Admit(
                            Raw.string(raw.get("command"), ""),
                            Raw.list(raw.get("from")).stream().map(String::valueOf).toList(),
                            Raw.stringOrNull(raw.get("when"))))
                    .toList();

            aggregate.stateMachine(new StateMachine(
                    contextName,
                    aggregateName,
                    Raw.string(document.get("initial"), ""),
                    states,
                    transitions,
                    admits));
        }

        for (Map<String, Object> document : byKind.getOrDefault("read-model", List.of())) {
            Map<String, Object> scope = Raw.record(document.get("scope"));
            String contextName = Raw.string(scope.get("boundedContext"), "default");
            List<ReadModel.Projection> projections = Raw.list(document.get("projections")).stream()
                    .map(Raw::record)
                    .map(raw -> new ReadModel.Projection(
                            Raw.string(raw.get("aggregate"), ""),
                            Raw.string(raw.get("event"), ""),
                            Raw.stringOrNull(raw.get("rule"))))
                    .toList();

            context(contexts, contextName, domainName)
                    .readModels()
                    .add(new ReadModel(
                            Raw.string(document.get("name"), ""),
                            domainName,
                            contextName,
                            Raw.stringOrNull(document.get("paradigm")),
                            Schema.fromRaw(Raw.record(document.get("schema"))),
                            projections));
        }

        for (Map<String, Object> document : byKind.getOrDefault("query", List.of())) {
            Map<String, Object> scope = Raw.record(document.get("scope"));
            String contextName = Raw.string(scope.get("boundedContext"), "default");
            context(contexts, contextName, domainName)
                    .queries()
                    .add(new Query(
                            Raw.string(document.get("name"), ""),
                            domainName,
                            contextName,
                            Raw.string(document.get("readModel"), ""),
                            Schema.fromRaw(Raw.record(document.get("parameters")))));
        }

        return new Model(
                domainName,
                List.copyOf(contexts.values()),
                parseFeatures(byKind.getOrDefault("feature", List.of()), domainName),
                parsePolicies(byKind.getOrDefault("policy", List.of()), domainName));
    }

    private static List<Policy> parsePolicies(List<Map<String, Object>> documents, String domainName) {
        List<Policy> policies = new ArrayList<>();
        for (Map<String, Object> document : documents) {
            List<Object> handles = Raw.list(document.get("handles"));
            List<Object> emits = Raw.list(document.get("emits"));
            if (handles.isEmpty() || emits.isEmpty()) {
                continue;
            }
            Map<String, Object> handle = Raw.record(handles.get(0));
            Map<String, Object> emit = Raw.record(emits.get(0));
            // only aggregate-bound handle/emit are supported for now
            if (!handle.containsKey("aggregate") || !emit.containsKey("aggregate")) {
                continue;
            }

            policies.add(new Policy(
                    Raw.string(document.get("name"), ""),
                    domainName,
                    Raw.string(handle.get("boundedContext"), "default"),
                    Raw.string(handle.get("aggregate"), ""),
                    Raw.string(handle.get("event"), ""),
                    Raw.string(emit.get("boundedContext"), "default"),
                    Raw.string(emit.get("aggregate"), ""),
                    Raw.string(emit.get("command"), "")));
        }

        return policies;
    }

    private static List<Feature> parseFeatures(List<Map<String, Object>> documents, String domainName) {
        List<Feature> features = new ArrayList<>();
        for (Map<String, Object> document : documents) {
            Map<String, Object> scope = Raw.record(document.get("scope"));
            // only the aggregate variant is supported for now
            if (!scope.containsKey("aggregate")) {
                continue;
            }

            List<Feature.Scenario> scenarios = Raw.list(document.get("scenarios")).stream()
                    .map(Raw::record)
                    .map(scenario -> {
                        Map<String, Object> when = Raw.record(scenario.get("when"));
                        Map<String, Object> then = Raw.record(scenario.get("then"));

                        return new Feature.Scenario(
                                Raw.string(scenario.get("name"), ""),
                                parseExamples(scenario.get("given")),
                                Raw.string(when.get("command"), ""),
                                Raw.record(when.get("data")),
                                parseExamples(then.get("events")),
                                then.containsKey("rejection")
                                        ? Raw.string(Raw.record(then.get("rejection")).get("reason"), "rejected")
                                        : null);
                    })
                    .toList();

            features.add(new Feature(
                    Raw.string(document.get("name"), ""),
                    domainName,
                    Raw.string(scope.get("boundedContext"), "default"),
                    Raw.string(scope.get("aggregate"), ""),
                    scenarios));
        }

        return features;
    }

    private static List<Feature.EventExample> parseExamples(Object raw) {
        return Raw.list(raw).stream()
                .map(Raw::record)
                .map(example ->
                        new Feature.EventExample(Raw.string(example.get("event"), ""), Raw.record(example.get("data"))))
                .toList();
    }

    private static BoundedContext context(Map<String, BoundedContext> contexts, String name, String domain) {
        return contexts.computeIfAbsent(name, key -> new BoundedContext(key, domain));
    }

    private static String singleDomainName(List<Map<String, Object>> domainDocuments) {
        if (domainDocuments.isEmpty()) {
            throw new IllegalArgumentException("Model contains no `domain` document.");
        }
        return Raw.string(domainDocuments.get(0).get("name"), "");
    }

    private static String annotation(Map<String, Object> document, String key) {
        Object value = Raw.record(Raw.record(document.get("metadata")).get("annotations")).get(key);
        return value == null ? null : String.valueOf(value);
    }
}
