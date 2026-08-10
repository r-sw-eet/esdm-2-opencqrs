package io.github.rsweet.esdm2opencqrs.adapter.opencqrs;

import io.github.rsweet.esdm2opencqrs.adapter.Adapter;
import io.github.rsweet.esdm2opencqrs.adapter.GeneratedProject;
import io.github.rsweet.esdm2opencqrs.feel.Feel;
import io.github.rsweet.esdm2opencqrs.feel.FeelException;
import io.github.rsweet.esdm2opencqrs.feel.FeelNode;
import io.github.rsweet.esdm2opencqrs.feel.Mapping;
import io.github.rsweet.esdm2opencqrs.model.Aggregate;
import io.github.rsweet.esdm2opencqrs.model.BoundedContext;
import io.github.rsweet.esdm2opencqrs.model.Command;
import io.github.rsweet.esdm2opencqrs.model.Event;
import io.github.rsweet.esdm2opencqrs.model.Feature;
import io.github.rsweet.esdm2opencqrs.model.Field;
import io.github.rsweet.esdm2opencqrs.model.Lifecycle;
import io.github.rsweet.esdm2opencqrs.model.Model;
import io.github.rsweet.esdm2opencqrs.model.Policy;
import io.github.rsweet.esdm2opencqrs.model.Query;
import io.github.rsweet.esdm2opencqrs.model.Raw;
import io.github.rsweet.esdm2opencqrs.model.ReadModel;
import io.github.rsweet.esdm2opencqrs.model.StateMachine;
import io.github.rsweet.esdm2opencqrs.support.Str;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Emits a runnable, dockerized Spring Boot application that implements the ESDM model with CQRS +
 * event sourcing on OpenCQRS and EventSourcingDB. Write side: HTTP command -&gt; {@code @CommandHandling}
 * -&gt; events appended on subject {@code /<aggregate>/<id>}. Read side: {@code @EventHandling}
 * processors project the same events into MongoDB read collections the query API reads. One process
 * serves HTTP and runs the projections.
 */
public class OpenCqrsAdapter implements Adapter {

    @Override
    public String name() {
        return "opencqrs-eventsourcingdb";
    }

    @Override
    public String description() {
        return "Spring Boot + OpenCQRS + EventSourcingDB + MongoDB read models (CQRS, event-sourced, Spring Web MVC).";
    }

    @Override
    public String slug() {
        return "opencqrs";
    }

    @Override
    public GeneratedProject generate(Model model, Map<String, Object> options) {
        String appName = Raw.string(options.get("appName"), model.domain());
        String basePackage = Raw.string(options.get("basePackage"), "app." + Naming.packageSegment(model.domain()));
        String source = Raw.string(options.get("source"), "https://esdm-extensions.io/" + model.domain());
        String bpmnSource = Raw.string(options.get("bpmnSource"), "");

        GeneratedProject project = new GeneratedProject();

        project.add("pom.xml", pom(appName));
        project.add("Dockerfile", dockerfile(appName));
        project.add("compose.yaml", compose());
        project.add(".dockerignore", Bootstrap.dockerignore());
        project.add(".env.example", envExample());
        project.add("README.md", readme(appName, model.domain()));
        project.add("src/main/resources/application.properties", applicationProperties(appName));
        project.add("src/main/resources/bpmn.xml", bpmnSource);
        project.add("src/main/resources/catalog.json", Json.write(Catalog.of(model)));

        project.add(Naming.sourcePath(basePackage, "Application"), Bootstrap.application(basePackage));
        project.add(
                Naming.sourcePath(basePackage + ".config", "NimbusEventDataMarshaller"),
                Bootstrap.marshaller(basePackage));
        project.add(
                Naming.sourcePath(basePackage + ".config", "MongoProgressTracker"),
                Bootstrap.progressTracker(basePackage));
        project.add(
                Naming.sourcePath(basePackage + ".config", "WebConfiguration"), Bootstrap.webConfiguration(basePackage));
        project.add(
                Naming.sourcePath(basePackage + ".config", "ApiExceptionHandler"),
                Bootstrap.exceptionHandler(basePackage));
        project.add(Naming.sourcePath(basePackage + ".support", "ApiError"), Bootstrap.apiError(basePackage));
        project.add(
                Naming.sourcePath(basePackage + ".support", "DomainRuleException"),
                Bootstrap.domainRuleException(basePackage));
        project.add(
                Naming.sourcePath(basePackage + ".support", "IllegalTransitionException"),
                Bootstrap.illegalTransition(basePackage));
        project.add(
                Naming.sourcePath(basePackage + ".support", "GuardViolationException"),
                Bootstrap.guardViolation(basePackage));
        project.add(Naming.sourcePath(basePackage + ".support", "Guards"), Bootstrap.guards(basePackage));
        project.add(Naming.sourcePath(basePackage + ".dev", "DevController"), devController(basePackage));
        emitStore(project, basePackage);
        project.add(
                Naming.sourcePath(basePackage + ".config", "EventTypeConfiguration"),
                eventTypeConfiguration(model, basePackage, source));

        for (BoundedContext context : model.boundedContexts()) {
            for (Aggregate aggregate : context.aggregates()) {
                emitEvents(project, basePackage, aggregate);
                emitCommands(project, basePackage, aggregate);
                emitState(project, basePackage, aggregate);
                emitHandlers(project, basePackage, aggregate);
                emitCommandController(project, basePackage, context, aggregate);
            }
            for (ReadModel readModel : context.readModels()) {
                emitRow(project, basePackage, context, readModel);
                emitRepository(project, basePackage, context, readModel);
                emitProjector(project, basePackage, context, readModel);
                emitQueryController(project, basePackage, context, readModel);
            }
        }

        emitPolicies(project, basePackage, model);
        emitTests(project, basePackage, model);

        return project;
    }

    // ---- store seam (override these to swap the event store) ---------------

    /** Build file; the Postgres target swaps dependencies and pins the interface-bearing build. */
    protected String pom(String appName) {
        return Bootstrap.pom(appName);
    }

    protected String dockerfile(String appName) {
        return Bootstrap.dockerfile(appName);
    }

    protected String compose() {
        return Bootstrap.compose();
    }

    protected String envExample() {
        return Bootstrap.envExample();
    }

    protected String readme(String appName, String domain) {
        return Bootstrap.readme(appName, domain);
    }

    protected String applicationProperties(String appName) {
        return Bootstrap.applicationProperties(appName);
    }

    /** The 0004 event window reads the raw store, so it is store-specific. */
    protected String devController(String basePackage) {
        return Bootstrap.devController(basePackage);
    }

    /** Emits an app-local event store. EventSourcingDB needs none: the starter auto-configures its client. */
    protected void emitStore(GeneratedProject project, String basePackage) {}

    // ---- write side --------------------------------------------------------

    private void emitEvents(GeneratedProject project, String basePackage, Aggregate aggregate) {
        String eventPackage = writePackage(basePackage, aggregate) + ".events";
        for (Event event : aggregate.events()) {
            String type = Naming.typeName(event.name()) + "Event";
            List<String> lines = new ArrayList<>();
            lines.add("package " + eventPackage + ";");
            lines.add("");
            lines.add("public record " + type + "(" + components(event.data().fields()) + ") {}");
            project.add(Naming.sourcePath(eventPackage, type), file(lines));
        }
    }

    private void emitCommands(GeneratedProject project, String basePackage, Aggregate aggregate) {
        String commandPackage = writePackage(basePackage, aggregate) + ".commands";
        for (Command command : aggregate.commands()) {
            String type = Naming.typeName(command.name()) + "Command";
            List<Field> fields = commandFields(aggregate, command);
            String condition = subjectCondition(aggregate, command);

            List<String> lines = new ArrayList<>();
            lines.add("package " + commandPackage + ";");
            lines.add("");
            lines.add("import com.opencqrs.framework.command.Command;");
            lines.add("");
            lines.add("public record " + type + "(" + components(fields) + ") implements Command {");
            lines.add("");
            lines.add("    @Override");
            lines.add("    public String getSubject() {");
            lines.add("        return \"/" + aggregate.name() + "/\" + " + Naming.memberName(aggregate.identityField())
                    + ";");
            lines.add("    }");
            if (condition.equals("NONE")) {
                lines.add("");
                lines.add("    // No EXISTS condition: the 0001 guard owns the rejection, so an unknown subject is");
                lines.add("    // answered as an illegal transition from \"undefined\" rather than a 404.");
            }
            lines.add("");
            lines.add("    @Override");
            lines.add("    public SubjectCondition getSubjectCondition() {");
            lines.add("        return SubjectCondition." + condition + ";");
            lines.add("    }");
            lines.add("}");
            project.add(Naming.sourcePath(commandPackage, type), file(lines));
        }
    }

    private void emitState(GeneratedProject project, String basePackage, Aggregate aggregate) {
        String writePackage = writePackage(basePackage, aggregate);
        String type = stateType(aggregate);

        List<String> components = new ArrayList<>();
        for (Field field : aggregate.state().fields()) {
            components.add(JavaTypes.of(field.jsonType()) + " " + Naming.memberName(field.name()));
        }
        if (aggregate.stateMachine() != null) {
            components.add("String status");
        }

        List<String> lines = new ArrayList<>();
        lines.add("package " + writePackage + ";");
        lines.add("");
        lines.add("/** Write model for the {@code " + aggregate.name() + "} aggregate."
                + (aggregate.stateMachine() != null
                        ? " {@code status} carries the 0001 state machine position."
                        : "")
                + " */");
        lines.add("public record " + type + "(" + String.join(", ", components) + ") {}");
        project.add(Naming.sourcePath(writePackage, type), file(lines));
    }

    private void emitHandlers(GeneratedProject project, String basePackage, Aggregate aggregate) {
        String writePackage = writePackage(basePackage, aggregate);
        String stateType = stateType(aggregate);
        String type = Naming.typeName(aggregate.name()) + "Handlers";
        StateMachine machine = aggregate.stateMachine();
        boolean needsAdmit = false;
        boolean needsGuard = false;

        List<String> body = new ArrayList<>();
        for (Command command : aggregate.commands()) {
            String commandType = Naming.typeName(command.name()) + "Command";
            boolean isCreate = command.lifecycle() == Lifecycle.CREATE;
            StateMachine.Admit admit = machine == null ? null : machine.admitFor(command.name());

            List<String> parameters = new ArrayList<>();
            if (!isCreate) {
                parameters.add(stateType + " state");
            }
            parameters.add(commandType + " command");
            parameters.add("Map<String, ?> metaData");
            parameters.add("CommandEventPublisher<" + stateType + "> publisher");

            body.add("    @CommandHandling");
            body.add("    public " + JavaTypes.of(identityField(aggregate).jsonType()) + " "
                    + Naming.memberName(command.name()) + "(");
            body.add("            " + String.join(", ", parameters) + ") {");

            if (admit != null && !admit.from().isEmpty()) {
                needsAdmit = true;
                String from = admit.from().stream().map(Q::string).collect(Collectors.joining(", "));
                body.add("        admit(" + Q.string(command.name()) + ", state, java.util.Set.of(" + from + "));");
            }
            if (admit != null && admit.when() != null && !admit.when().isEmpty()) {
                needsGuard = true;
                body.add("        if (state == null || !(" + compileGuard(admit.when(), basePackage) + ")) {");
                body.add("            throw new GuardViolationException(" + Q.string(command.name()) + ", "
                        + Q.string(admit.when()) + ");");
                body.add("        }");
            }

            for (String eventName : command.publishes()) {
                Event event = aggregate.event(eventName);
                if (event == null) {
                    continue;
                }
                body.add("        publisher.publish(new " + Naming.typeName(event.name()) + "Event("
                        + eventArguments(aggregate, command, event, isCreate) + "), metaData);");
            }

            body.add("        return command." + Naming.memberName(aggregate.identityField()) + "();");
            body.add("    }");
            body.add("");
        }

        for (Event event : aggregate.events()) {
            body.add("    @StateRebuilding");
            body.add("    public " + stateType + " on" + Naming.typeName(event.name()) + "("
                    + Naming.typeName(event.name()) + "Event event, " + stateType + " state) {");
            body.add("        return new " + stateType + "(" + stateArguments(aggregate, event) + ");");
            body.add("    }");
            body.add("");
        }

        if (needsAdmit) {
            body.add("    private static void admit(String command, " + stateType
                    + " state, java.util.Set<String> from) {");
            body.add("        String status = state == null || state.status() == null ? \"undefined\" : state.status();");
            body.add("        if (!from.contains(status)) {");
            body.add("            throw new IllegalTransitionException(command, status);");
            body.add("        }");
            body.add("    }");
            body.add("");
        }

        List<String> lines = new ArrayList<>();
        lines.add("package " + writePackage + ";");
        lines.add("");
        for (Command command : aggregate.commands()) {
            lines.add("import " + writePackage + ".commands." + Naming.typeName(command.name()) + "Command;");
        }
        for (Event event : aggregate.events()) {
            lines.add("import " + writePackage + ".events." + Naming.typeName(event.name()) + "Event;");
        }
        if (needsAdmit) {
            lines.add("import " + basePackage + ".support.IllegalTransitionException;");
        }
        if (needsGuard) {
            lines.add("import " + basePackage + ".support.GuardViolationException;");
        }
        lines.add("import com.opencqrs.framework.command.CommandEventPublisher;");
        lines.add("import com.opencqrs.framework.command.CommandHandlerConfiguration;");
        lines.add("import com.opencqrs.framework.command.CommandHandling;");
        lines.add("import com.opencqrs.framework.command.StateRebuilding;");
        lines.add("import java.util.Map;");
        lines.add("");
        lines.add("/** Decide (command handling) and evolve (state rebuilding) for the {@code " + aggregate.name()
                + "} aggregate. */");
        lines.add("@CommandHandlerConfiguration");
        lines.add("public class " + type + " {");
        lines.add("");
        lines.addAll(body);
        lines.add("}");

        project.add(Naming.sourcePath(writePackage, type), file(lines));
    }

    private void emitCommandController(
            GeneratedProject project, String basePackage, BoundedContext context, Aggregate aggregate) {
        if (aggregate.commands().isEmpty()) {
            return;
        }
        String writePackage = writePackage(basePackage, aggregate);
        String type = Naming.typeName(aggregate.name()) + "Controller";

        List<String> lines = new ArrayList<>();
        lines.add("package " + writePackage + ";");
        lines.add("");
        for (Command command : aggregate.commands()) {
            lines.add("import " + writePackage + ".commands." + Naming.typeName(command.name()) + "Command;");
        }
        lines.add("import com.opencqrs.framework.command.CommandRouter;");
        lines.add("import java.util.Map;");
        lines.add("import java.util.UUID;");
        lines.add("import org.springframework.web.bind.annotation.PostMapping;");
        lines.add("import org.springframework.web.bind.annotation.RequestBody;");
        lines.add("import org.springframework.web.bind.annotation.RequestHeader;");
        lines.add("import org.springframework.web.bind.annotation.RequestMapping;");
        lines.add("import org.springframework.web.bind.annotation.RestController;");
        lines.add("");
        lines.add("@RestController");
        lines.add("@RequestMapping(\"/" + context.name() + "\")");
        lines.add("public class " + type + " {");
        lines.add("");
        lines.add("    private static final String CORRELATION_HEADER = \"X-Correlation-ID\";");
        lines.add("");
        lines.add("    private final CommandRouter commandRouter;");
        lines.add("");
        lines.add("    public " + type + "(CommandRouter commandRouter) {");
        lines.add("        this.commandRouter = commandRouter;");
        lines.add("    }");
        lines.add("");

        for (Command command : aggregate.commands()) {
            String inputType = Naming.typeName(command.name()) + "Input";
            lines.add("    public record " + inputType + "(" + components(command.data().fields()) + ") {}");
            lines.add("");
        }

        for (Command command : aggregate.commands()) {
            String commandType = Naming.typeName(command.name()) + "Command";
            String inputType = Naming.typeName(command.name()) + "Input";
            String identityType = JavaTypes.of(identityField(aggregate).jsonType());

            lines.add("    @PostMapping(\"/" + command.name() + "\")");
            lines.add("    public Map<String, Object> " + Naming.memberName(command.name()) + "(");
            lines.add("            @RequestBody " + inputType + " input,");
            lines.add("            @RequestHeader(name = CORRELATION_HEADER, required = false) String correlationId) {");
            lines.add("        " + identityType + " id = commandRouter.send(");
            lines.add("                new " + commandType + "(" + commandArguments(aggregate, command)
                    + "), metaData(correlationId));");
            lines.add("        return Map.of(\"id\", id);");
            lines.add("    }");
            lines.add("");
        }

        lines.add("    private static Map<String, Object> metaData(String correlationId) {");
        lines.add("        return Map.of(\"correlationid\", correlationId == null"
                + " ? UUID.randomUUID().toString() : correlationId);");
        lines.add("    }");
        lines.add("}");

        project.add(Naming.sourcePath(writePackage, type), file(lines));
    }

    // ---- read side ---------------------------------------------------------

    private void emitRow(GeneratedProject project, String basePackage, BoundedContext context, ReadModel readModel) {
        String readPackage = readPackage(basePackage, context, readModel);
        String type = Naming.typeName(readModel.name()) + "Row";
        String primaryKey = primaryKey(readModel);

        List<String> components = new ArrayList<>();
        for (Field column : readModel.columns().fields()) {
            String prefix = column.name().equals(primaryKey) ? "@Id " : "";
            components.add(prefix + JavaTypes.of(column.jsonType()) + " " + Naming.memberName(column.name()));
        }

        List<String> lines = new ArrayList<>();
        lines.add("package " + readPackage + ";");
        lines.add("");
        lines.add("import org.springframework.data.annotation.Id;");
        lines.add("import org.springframework.data.mongodb.core.mapping.Document;");
        lines.add("");
        lines.add("/** Row keys must match the {@code columns[].name} of the {@code " + readModel.name()
                + "} read model in the 0004 catalog. */");
        lines.add("@Document(collection = \"" + Naming.collection(readModel.name()) + "\")");
        lines.add("public record " + type + "(" + String.join(", ", components) + ") {}");
        project.add(Naming.sourcePath(readPackage, type), file(lines));
    }

    private void emitRepository(
            GeneratedProject project, String basePackage, BoundedContext context, ReadModel readModel) {
        String readPackage = readPackage(basePackage, context, readModel);
        String type = Naming.typeName(readModel.name()) + "Repository";
        String rowType = Naming.typeName(readModel.name()) + "Row";
        String keyType = JavaTypes.of(columnType(readModel, primaryKey(readModel)));

        List<String> lines = new ArrayList<>();
        lines.add("package " + readPackage + ";");
        lines.add("");
        lines.add("import org.springframework.data.mongodb.repository.MongoRepository;");
        lines.add("");
        lines.add("public interface " + type + " extends MongoRepository<" + rowType + ", " + keyType + "> {}");
        project.add(Naming.sourcePath(readPackage, type), file(lines));
    }

    private void emitProjector(
            GeneratedProject project, String basePackage, BoundedContext context, ReadModel readModel) {
        List<Event> events = projectedEvents(context, readModel);
        if (events.isEmpty()) {
            return;
        }
        String readPackage = readPackage(basePackage, context, readModel);
        String type = Naming.typeName(readModel.name()) + "Projector";
        String rowType = Naming.typeName(readModel.name()) + "Row";
        String repositoryType = Naming.typeName(readModel.name()) + "Repository";
        Event anchor = anchorEvent(events);
        String primaryKey = primaryKey(readModel);

        List<String> lines = new ArrayList<>();
        lines.add("package " + readPackage + ";");
        lines.add("");
        for (Event event : events) {
            lines.add("import " + basePackage + ".write." + Naming.packageSegment(event.boundedContext()) + "."
                    + Naming.packageSegment(event.aggregate()) + ".events." + Naming.typeName(event.name())
                    + "Event;");
        }
        lines.add("import com.opencqrs.framework.eventhandler.EventHandling;");
        lines.add("import org.springframework.stereotype.Component;");
        lines.add("");
        lines.add("@Component");
        lines.add("public class " + type + " {");
        lines.add("");
        lines.add("    private static final String GROUP = \"" + readModel.name() + "\";");
        lines.add("");
        lines.add("    private final " + repositoryType + " repository;");
        lines.add("");
        lines.add("    public " + type + "(" + repositoryType + " repository) {");
        lines.add("        this.repository = repository;");
        lines.add("    }");
        lines.add("");

        for (Event event : events) {
            Aggregate aggregate = aggregateOf(context, event.aggregate());
            String identity = aggregate == null ? primaryKey : aggregate.identityField();
            String key = event.data().has(primaryKey) ? primaryKey : identity;
            String keyExpression = "event." + Naming.memberName(key) + "()";

            lines.add("    @EventHandling(GROUP)");
            lines.add("    public void on" + Naming.typeName(event.name()) + "(" + Naming.typeName(event.name())
                    + "Event event) {");

            if (event.equals(anchor)) {
                lines.add("        repository.save(new " + rowType + "("
                        + rowArguments(readModel, event, primaryKey, identity, key, null) + "));");
            } else if (event.lifecycle() == Lifecycle.DELETE) {
                lines.add("        repository.deleteById(" + keyExpression + ");");
            } else {
                lines.add("        repository.findById(" + keyExpression + ")");
                lines.add("                .ifPresent(row -> repository.save(new " + rowType + "("
                        + rowArguments(readModel, event, primaryKey, identity, key, "row") + ")));");
            }

            lines.add("    }");
            lines.add("");
        }

        lines.add("}");
        project.add(Naming.sourcePath(readPackage, type), file(lines));
    }

    private void emitQueryController(
            GeneratedProject project, String basePackage, BoundedContext context, ReadModel readModel) {
        List<Query> queries = context.queries().stream()
                .filter(query -> query.readModel().equals(readModel.name()))
                .toList();
        if (queries.isEmpty()) {
            return;
        }
        String readPackage = readPackage(basePackage, context, readModel);
        String type = Naming.typeName(readModel.name()) + "QueryController";
        String rowType = Naming.typeName(readModel.name()) + "Row";
        String repositoryType = Naming.typeName(readModel.name()) + "Repository";
        String entity = Naming.typeName(readModel.name());
        String errorCode = Str.constant(readModel.name()) + "_NOT_FOUND";
        boolean hasGet = queries.stream().anyMatch(Query::isGet);

        List<String> lines = new ArrayList<>();
        lines.add("package " + readPackage + ";");
        lines.add("");
        if (hasGet) {
            lines.add("import static " + basePackage + ".support.ApiError.details;");
            lines.add("");
            lines.add("import " + basePackage + ".support.ApiError;");
        }
        lines.add("import java.util.List;");
        if (hasGet) {
            lines.add("import org.springframework.http.HttpStatus;");
            lines.add("import org.springframework.http.ResponseEntity;");
        }
        lines.add("import org.springframework.web.bind.annotation.GetMapping;");
        lines.add("import org.springframework.web.bind.annotation.RequestMapping;");
        if (hasGet) {
            lines.add("import org.springframework.web.bind.annotation.RequestParam;");
        }
        lines.add("import org.springframework.web.bind.annotation.RestController;");
        lines.add("");
        lines.add("@RestController");
        lines.add("@RequestMapping(\"/" + context.name() + "\")");
        lines.add("public class " + type + " {");
        lines.add("");
        lines.add("    private final " + repositoryType + " repository;");
        lines.add("");
        lines.add("    public " + type + "(" + repositoryType + " repository) {");
        lines.add("        this.repository = repository;");
        lines.add("    }");
        lines.add("");

        for (Query query : queries) {
            if (query.isGet()) {
                Field parameter = query.parameters().fields().get(0);
                lines.add("    @GetMapping(\"/" + query.name() + "\")");
                lines.add("    public ResponseEntity<?> " + Naming.memberName(query.name()) + "(@RequestParam(\""
                        + parameter.name() + "\") " + JavaTypes.of(parameter.jsonType()) + " "
                        + Naming.memberName(parameter.name()) + ") {");
                lines.add("        return repository");
                lines.add("                .findById(" + Naming.memberName(parameter.name()) + ")");
                lines.add("                .<ResponseEntity<?>>map(ResponseEntity::ok)");
                lines.add("                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)");
                lines.add("                        .body(new ApiError(");
                lines.add("                                \"NOT_FOUND\",");
                lines.add("                                " + Q.string(entity + " not found") + ",");
                lines.add("                                details(");
                lines.add("                                        \"errorCode\",");
                lines.add("                                        " + Q.string(errorCode) + ",");
                lines.add("                                        \"reason\",");
                lines.add("                                        " + Q.string("Could not find " + entity
                        + " matching the given filter") + "))));");
                lines.add("    }");
            } else {
                lines.add("    @GetMapping(\"/" + query.name() + "\")");
                lines.add("    public List<" + rowType + "> " + Naming.memberName(query.name()) + "() {");
                lines.add("        return repository.findAll();");
                lines.add("    }");
            }
            lines.add("");
        }

        lines.add("}");
        project.add(Naming.sourcePath(readPackage, type), file(lines));
    }

    // ---- policies ----------------------------------------------------------

    private void emitPolicies(GeneratedProject project, String basePackage, Model model) {
        String policyPackage = basePackage + ".policies";
        for (Policy policy : model.policies()) {
            Aggregate handleAggregate = model.aggregate(policy.handleContext(), policy.handleAggregate());
            Aggregate emitAggregate = model.aggregate(policy.emitContext(), policy.emitAggregate());
            if (handleAggregate == null || emitAggregate == null) {
                continue;
            }
            Event event = handleAggregate.event(policy.handleEvent());
            Command command = emitAggregate.command(policy.emitCommand());
            if (event == null || command == null) {
                continue;
            }

            String type = Naming.typeName(policy.name()) + "Policy";
            String eventType = Naming.typeName(event.name()) + "Event";
            String commandType = Naming.typeName(command.name()) + "Command";

            // A declared mapping (proposal 0005) wins per field; everything it leaves out falls back
            // to the convention below, which is what that proposal documents as the default.
            Map<String, FeelNode> mapping = policy.mapping().isEmpty()
                    ? Map.of()
                    : Mapping.parse(policy.mapping());

            // The emitted command's "<handled aggregate>Id" field carries the handled event's identity;
            // the reacting aggregate's own id is minted here, the way a create controller would.
            String crossReference = Naming.memberName(policy.handleAggregate() + "-id");
            List<String> arguments = new ArrayList<>();
            for (Field field : commandFields(emitAggregate, command)) {
                FeelNode assigned = mapping.get(field.name());
                if (assigned != null) {
                    arguments.add(FeelJava.compile(assigned, basePackage, "event"));
                } else if (Naming.memberName(field.name()).equals(crossReference)) {
                    arguments.add("event." + Naming.memberName(handleAggregate.identityField()) + "()");
                } else if (field.name().equals(emitAggregate.identityField())
                        && !command.data().has(field.name())) {
                    arguments.add("UUID.randomUUID().toString()");
                } else if (event.data().has(field.name())) {
                    arguments.add("event." + Naming.memberName(field.name()) + "()");
                } else {
                    arguments.add(JavaTypes.defaultLiteral(field));
                }
            }

            List<String> lines = new ArrayList<>();
            lines.add("package " + policyPackage + ";");
            lines.add("");
            lines.add("import " + basePackage + ".write." + Naming.packageSegment(event.boundedContext()) + "."
                    + Naming.packageSegment(event.aggregate()) + ".events." + eventType + ";");
            lines.add("import " + basePackage + ".write." + Naming.packageSegment(command.boundedContext()) + "."
                    + Naming.packageSegment(command.aggregate()) + ".commands." + commandType + ";");
            lines.add("import com.opencqrs.framework.command.CommandRouter;");
            lines.add("import com.opencqrs.framework.eventhandler.EventHandling;");
            lines.add("import java.util.UUID;");
            lines.add("import org.slf4j.Logger;");
            lines.add("import org.slf4j.LoggerFactory;");
            lines.add("import org.springframework.stereotype.Component;");
            lines.add("");
            lines.add("/** Reaction (ESDM policy): {@code " + policy.handleEvent() + "} triggers {@code "
                    + policy.emitCommand() + "}. */");
            lines.add("@Component");
            lines.add("public class " + type + " {");
            lines.add("");
            lines.add("    private static final Logger log = LoggerFactory.getLogger(" + type + ".class);");
            lines.add("");
            lines.add("    private final CommandRouter commandRouter;");
            lines.add("");
            lines.add("    public " + type + "(CommandRouter commandRouter) {");
            lines.add("        this.commandRouter = commandRouter;");
            lines.add("    }");
            lines.add("");
            lines.add("    @EventHandling(\"" + policy.name() + "\")");
            lines.add("    public void on" + Naming.typeName(event.name()) + "(" + eventType + " event) {");
            lines.add("        try {");
            lines.add("            commandRouter.send(new " + commandType + "(" + String.join(", ", arguments)
                    + "));");
            lines.add("        } catch (RuntimeException e) {");
            // A refused reaction must not wedge the processor: the model declares at-most-once delivery.
            lines.add("            log.error(\"policy " + policy.name() + " failed to dispatch "
                    + policy.emitCommand() + "\", e);");
            lines.add("        }");
            lines.add("    }");
            lines.add("}");

            project.add(Naming.sourcePath(policyPackage, type), file(lines));
        }
    }

    // ---- emitted tests -----------------------------------------------------

    private void emitTests(GeneratedProject project, String basePackage, Model model) {
        for (Feature feature : model.features()) {
            Aggregate aggregate = model.aggregate(feature.boundedContext(), feature.aggregate());
            if (aggregate == null || feature.scenarios().isEmpty()) {
                continue;
            }
            String writePackage = writePackage(basePackage, aggregate);
            String type = Naming.typeName(feature.name()) + "Test";
            String fallbackId = fallbackIdentity(aggregate, feature);

            Set<String> commandImports = new LinkedHashSet<>();
            Set<String> eventImports = new LinkedHashSet<>();
            boolean needsDomainRule = false;

            List<String> body = new ArrayList<>();
            for (Feature.Scenario scenario : feature.scenarios()) {
                Command command = aggregate.command(scenario.commandName());
                if (command == null) {
                    continue;
                }
                String commandType = Naming.typeName(command.name()) + "Command";
                commandImports.add(commandType);

                body.add("    @Test");
                body.add("    void " + Naming.memberName(scenario.name()) + "(@Autowired CommandHandlingTestFixture<"
                        + commandType + "> fixture) {");
                body.add("        fixture.given()");

                if (scenario.given().isEmpty()) {
                    body.add("                .nothing()");
                } else {
                    List<String> givens = new ArrayList<>();
                    for (Feature.EventExample example : scenario.given()) {
                        Event event = aggregate.event(example.event());
                        if (event == null) {
                            continue;
                        }
                        eventImports.add(Naming.typeName(event.name()) + "Event");
                        givens.add("new " + Naming.typeName(event.name()) + "Event("
                                + exampleArguments(event, example, fallbackId) + ")");
                    }
                    body.add("                .events(" + String.join(", ", givens) + ")");
                }

                body.add("                .when(new " + commandType + "("
                        + scenarioCommandArguments(aggregate, command, scenario, fallbackId) + "))");

                if (scenario.isRejection()) {
                    StateMachine machine = aggregate.stateMachine();
                    StateMachine.Admit admit = machine == null ? null : machine.admitFor(command.name());
                    if (admit == null) {
                        body.add("                .fails();");
                    } else {
                        // The model states that the command is refused, not which rule refuses it.
                        needsDomainRule = true;
                        body.add("                .fails()");
                        body.add("                .throwing(DomainRuleException.class);");
                    }
                } else {
                    List<String> expected = new ArrayList<>();
                    for (Feature.EventExample example : scenario.thenEvents()) {
                        Event event = aggregate.event(example.event());
                        if (event == null) {
                            continue;
                        }
                        eventImports.add(Naming.typeName(event.name()) + "Event");
                        expected.add("new " + Naming.typeName(event.name()) + "Event("
                                + exampleArguments(event, example, fallbackId) + ")");
                    }
                    body.add("                .succeeds()");
                    body.add("                .allEvents()");
                    body.add("                .exactly(" + String.join(", ", expected) + ");");
                }

                body.add("    }");
                body.add("");
            }

            if (body.isEmpty()) {
                continue;
            }

            List<String> lines = new ArrayList<>();
            lines.add("package " + writePackage + ";");
            lines.add("");
            if (needsDomainRule) {
                lines.add("import " + basePackage + ".support.DomainRuleException;");
            }
            commandImports.forEach(name -> lines.add("import " + writePackage + ".commands." + name + ";"));
            eventImports.forEach(name -> lines.add("import " + writePackage + ".events." + name + ";"));
            lines.add("import com.opencqrs.framework.command.CommandHandlingTest;");
            lines.add("import com.opencqrs.framework.command.CommandHandlingTestFixture;");
            lines.add("import org.junit.jupiter.api.Test;");
            lines.add("import org.springframework.beans.factory.annotation.Autowired;");
            lines.add("");
            lines.add("/** One test per scenario of the {@code " + feature.name() + "} GWT feature. */");
            lines.add("@CommandHandlingTest");
            lines.add("class " + type + " {");
            lines.add("");
            lines.addAll(body);
            lines.add("}");

            project.add(Naming.testSourcePath(writePackage, type), file(lines));
        }
    }

    // ---- configuration -----------------------------------------------------

    private String eventTypeConfiguration(Model model, String basePackage, String source) {
        List<Event> events = model.aggregates().stream()
                .flatMap(aggregate -> aggregate.events().stream())
                .toList();

        List<String> lines = new ArrayList<>();
        lines.add("package " + basePackage + ".config;");
        lines.add("");
        for (Event event : events) {
            lines.add("import " + basePackage + ".write." + Naming.packageSegment(event.boundedContext()) + "."
                    + Naming.packageSegment(event.aggregate()) + ".events." + Naming.typeName(event.name())
                    + "Event;");
        }
        lines.add("import com.opencqrs.framework.persistence.EventSource;");
        lines.add("import com.opencqrs.framework.serialization.EventDataMarshaller;");
        lines.add("import com.opencqrs.framework.types.EventTypeResolver;");
        lines.add("import com.opencqrs.framework.types.PreconfiguredAssignableClassEventTypeResolver;");
        lines.add("import java.util.LinkedHashMap;");
        lines.add("import java.util.Map;");
        lines.add("import org.springframework.context.annotation.Bean;");
        lines.add("import org.springframework.context.annotation.Configuration;");
        lines.add("import tools.jackson.databind.ObjectMapper;");
        lines.add("");
        lines.add("/**");
        lines.add(" * Event wire types and envelope. The resolver map must be complete: once a custom");
        lines.add(" * {@link EventTypeResolver} is defined there is no class-name fallback.");
        lines.add(" */");
        lines.add("@Configuration");
        lines.add("public class EventTypeConfiguration {");
        lines.add("");
        lines.add("    @Bean");
        lines.add("    public EventTypeResolver eventTypeResolver() {");
        lines.add("        Map<String, Class<?>> types = new LinkedHashMap<>();");
        for (Event event : events) {
            lines.add("        types.put(" + Q.string(event.type()) + ", " + Naming.typeName(event.name())
                    + "Event.class);");
        }
        lines.add("        return new PreconfiguredAssignableClassEventTypeResolver(types);");
        lines.add("    }");
        lines.add("");
        lines.add("    @Bean");
        lines.add("    public EventDataMarshaller eventDataMarshaller(ObjectMapper objectMapper) {");
        lines.add("        return new NimbusEventDataMarshaller(objectMapper);");
        lines.add("    }");
        lines.add("");
        lines.add("    /** The family's CloudEvents source; OpenCQRS would default to {@code tag://<application name>}. */");
        lines.add("    @Bean");
        lines.add("    public EventSource eventSource() {");
        lines.add("        return new EventSource(" + Q.string(source) + ");");
        lines.add("    }");
        lines.add("}");

        return file(lines);
    }

    // ---- argument builders -------------------------------------------------

    /** Event constructor arguments inside a command handler. */
    private String eventArguments(Aggregate aggregate, Command command, Event event, boolean isCreate) {
        List<String> arguments = new ArrayList<>();
        for (Field field : event.data().fields()) {
            if (command.data().has(field.name()) || field.name().equals(aggregate.identityField())) {
                arguments.add("command." + Naming.memberName(field.name()) + "()");
            } else if (isCreate || !aggregate.state().has(field.name())) {
                arguments.add(JavaTypes.nullLiteral(field));
            } else {
                arguments.add("state." + Naming.memberName(field.name()) + "()");
            }
        }
        return String.join(", ", arguments);
    }

    /** State constructor arguments inside a {@code @StateRebuilding} method. */
    private String stateArguments(Aggregate aggregate, Event event) {
        boolean isCreate = event.lifecycle() == Lifecycle.CREATE;
        List<String> arguments = new ArrayList<>();
        for (Field field : aggregate.state().fields()) {
            if (event.data().has(field.name())) {
                arguments.add("event." + Naming.memberName(field.name()) + "()");
            } else if (isCreate) {
                arguments.add(JavaTypes.nullLiteral(field));
            } else {
                arguments.add("state." + Naming.memberName(field.name()) + "()");
            }
        }

        StateMachine machine = aggregate.stateMachine();
        if (machine != null) {
            String target = machine.transitionTarget(event.name());
            if (target != null && !target.isEmpty()) {
                arguments.add(Q.string(target));
            } else if (isCreate) {
                arguments.add(Q.string(machine.initial()));
            } else {
                arguments.add("state.status()");
            }
        }

        return String.join(", ", arguments);
    }

    /** Command constructor arguments inside an HTTP controller. */
    private String commandArguments(Aggregate aggregate, Command command) {
        List<String> arguments = new ArrayList<>();
        for (Field field : commandFields(aggregate, command)) {
            if (command.data().has(field.name())) {
                arguments.add("input." + Naming.memberName(field.name()) + "()");
            } else {
                // Server-minted aggregate id for a create command.
                arguments.add("UUID.randomUUID().toString()");
            }
        }
        return String.join(", ", arguments);
    }

    /** Read-model row constructor arguments; {@code existing} is the variable holding the current row, if any. */
    private String rowArguments(
            ReadModel readModel, Event event, String primaryKey, String identity, String key, String existing) {
        List<String> arguments = new ArrayList<>();
        for (Field column : readModel.columns().fields()) {
            if (column.name().equals(primaryKey)) {
                arguments.add(existing == null
                        ? "event." + Naming.memberName(key) + "()"
                        : existing + "." + Naming.memberName(column.name()) + "()");
            } else if (event.data().has(column.name())) {
                arguments.add("event." + Naming.memberName(column.name()) + "()");
            } else if (existing != null) {
                arguments.add(existing + "." + Naming.memberName(column.name()) + "()");
            } else if (column.name().equals(identity)) {
                arguments.add("event." + Naming.memberName(identity) + "()");
            } else {
                arguments.add(JavaTypes.nullLiteral(column));
            }
        }
        return String.join(", ", arguments);
    }

    /** Event constructor arguments from a GWT example's literal data. */
    private String exampleArguments(Event event, Feature.EventExample example, String fallbackId) {
        List<String> arguments = new ArrayList<>();
        for (Field field : event.data().fields()) {
            Object value = example.data().get(field.name());
            arguments.add(value == null ? fallbackOrNull(field, fallbackId) : literal(field, value));
        }
        return String.join(", ", arguments);
    }

    private String scenarioCommandArguments(
            Aggregate aggregate, Command command, Feature.Scenario scenario, String fallbackId) {
        List<String> arguments = new ArrayList<>();
        for (Field field : commandFields(aggregate, command)) {
            Object value = scenario.commandData().get(field.name());
            if (value != null) {
                arguments.add(literal(field, value));
            } else if (field.name().equals(aggregate.identityField())) {
                arguments.add(fallbackId);
            } else {
                arguments.add(JavaTypes.nullLiteral(field));
            }
        }
        return String.join(", ", arguments);
    }

    private String fallbackOrNull(Field field, String fallbackId) {
        return field.identity() ? fallbackId : JavaTypes.nullLiteral(field);
    }

    /**
     * The aggregate id used by emitted tests when a scenario does not carry one (a create command
     * mints it at runtime): the first id any example in the feature declares, else a fixed literal.
     */
    private String fallbackIdentity(Aggregate aggregate, Feature feature) {
        for (Feature.Scenario scenario : feature.scenarios()) {
            Object fromCommand = scenario.commandData().get(aggregate.identityField());
            if (fromCommand != null) {
                return Q.string(String.valueOf(fromCommand));
            }
            for (Feature.EventExample example : scenario.thenEvents()) {
                Object value = example.data().get(aggregate.identityField());
                if (value != null) {
                    return Q.string(String.valueOf(value));
                }
            }
            for (Feature.EventExample example : scenario.given()) {
                Object value = example.data().get(aggregate.identityField());
                if (value != null) {
                    return Q.string(String.valueOf(value));
                }
            }
        }
        return Q.string("00000000-0000-0000-0000-000000000000");
    }

    private String literal(Field field, Object value) {
        return switch (field.jsonType()) {
            case "boolean" -> Boolean.parseBoolean(String.valueOf(value)) ? "true" : "false";
            case "number" -> Double.parseDouble(String.valueOf(value)) + "d";
            case "integer" -> Long.parseLong(String.valueOf(value)) + "L";
            default -> Q.string(String.valueOf(value));
        };
    }

    private String compileGuard(String expression, String basePackage) {
        try {
            return FeelJava.compile(Feel.parse(expression), basePackage);
        } catch (FeelException e) {
            throw new IllegalStateException("invalid FEEL guard \"" + expression + "\": " + e.getMessage(), e);
        }
    }

    // ---- model helpers -----------------------------------------------------

    /** Command record components: the aggregate id first, then the modeled data fields. */
    static List<Field> commandFields(Aggregate aggregate, Command command) {
        List<Field> fields = new ArrayList<>();
        if (!command.data().has(aggregate.identityField())) {
            fields.add(identityField(aggregate));
        }
        fields.addAll(command.data().fields());
        return fields;
    }

    private static Field identityField(Aggregate aggregate) {
        Field field = aggregate.state().field(aggregate.identityField());
        return field != null ? field : new Field(aggregate.identityField(), "string", true, null, false, true);
    }

    private String subjectCondition(Aggregate aggregate, Command command) {
        if (command.lifecycle() == Lifecycle.CREATE) {
            return "PRISTINE";
        }
        StateMachine machine = aggregate.stateMachine();
        StateMachine.Admit admit = machine == null ? null : machine.admitFor(command.name());
        return admit != null && !admit.from().isEmpty() ? "NONE" : "EXISTS";
    }

    static String primaryKey(ReadModel readModel) {
        for (Field column : readModel.columns().fields()) {
            if (column.identity()) {
                return column.name();
            }
        }
        return readModel.columns().fields().isEmpty()
                ? "id"
                : readModel.columns().fields().get(0).name();
    }

    private static String columnType(ReadModel readModel, String columnName) {
        Field column = readModel.columns().field(columnName);
        return column == null ? "string" : column.jsonType();
    }

    static List<Event> projectedEvents(BoundedContext context, ReadModel readModel) {
        List<Event> events = new ArrayList<>();
        for (ReadModel.Projection projection : readModel.projections()) {
            Aggregate aggregate = aggregateOf(context, projection.aggregate());
            Event event = aggregate == null ? null : aggregate.event(projection.event());
            if (event != null && !events.contains(event)) {
                events.add(event);
            }
        }
        return events;
    }

    /** The event that creates a row: the first create-lifecycle event, else simply the first. */
    private static Event anchorEvent(List<Event> events) {
        return events.stream()
                .filter(event -> event.lifecycle() == Lifecycle.CREATE)
                .findFirst()
                .orElse(events.isEmpty() ? null : events.get(0));
    }

    private static Aggregate aggregateOf(BoundedContext context, String name) {
        return context.aggregates().stream()
                .filter(aggregate -> aggregate.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    private static String writePackage(String basePackage, Aggregate aggregate) {
        return basePackage + ".write." + Naming.packageSegment(aggregate.boundedContext()) + "."
                + Naming.packageSegment(aggregate.name());
    }

    private static String readPackage(String basePackage, BoundedContext context, ReadModel readModel) {
        return basePackage + ".read." + Naming.packageSegment(context.name()) + "."
                + Naming.packageSegment(readModel.name());
    }

    private static String stateType(Aggregate aggregate) {
        return Naming.typeName(aggregate.name()) + "State";
    }

    private static String components(List<Field> fields) {
        return fields.stream()
                .map(field -> JavaTypes.of(field.jsonType()) + " " + Naming.memberName(field.name()))
                .collect(Collectors.joining(", "));
    }

    private static String file(List<String> lines) {
        return String.join("\n", lines) + "\n";
    }

    /** The 0004 catalog, built from the model. */
    private static final class Catalog {

        static Map<String, Object> of(Model model) {
            List<Object> contexts = new ArrayList<>();
            for (BoundedContext context : model.boundedContexts()) {
                String base = "/" + context.name();

                List<Object> commands = new ArrayList<>();
                for (Aggregate aggregate : context.aggregates()) {
                    Map<String, Object> hints = FeelHints.of(aggregate);
                    for (Command command : aggregate.commands()) {
                        List<Object> fields = new ArrayList<>();
                        for (Field field : command.data().fields()) {
                            Map<String, Object> entry = new LinkedHashMap<>();
                            entry.put("name", Naming.memberName(field.name()));
                            entry.put("type", field.jsonType());
                            entry.put("feel", hints.get(field.name()));
                            fields.add(entry);
                        }

                        Object guard = null;
                        StateMachine machine = aggregate.stateMachine();
                        StateMachine.Admit admit = machine == null ? null : machine.admitFor(command.name());
                        if (admit != null && (!admit.from().isEmpty() || admit.when() != null)) {
                            Map<String, Object> entry = new LinkedHashMap<>();
                            entry.put("from", admit.from());
                            entry.put("when", admit.when());
                            guard = entry;
                        }

                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("name", command.name());
                        entry.put("lifecycle", command.lifecycle().wireName());
                        entry.put("path", base + "/" + command.name());
                        entry.put("fields", fields);
                        entry.put("guard", guard);
                        commands.add(entry);
                    }
                }

                List<Object> queries = new ArrayList<>();
                for (Query query : context.queries()) {
                    List<Object> params = new ArrayList<>();
                    for (Field field : query.parameters().fields()) {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("name", Naming.memberName(field.name()));
                        entry.put("type", field.jsonType());
                        params.add(entry);
                    }
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("name", query.name());
                    entry.put("path", base + "/" + query.name());
                    entry.put("kind", query.isGet() ? "get" : "list");
                    entry.put("params", params);
                    entry.put("readModel", query.readModel());
                    queries.add(entry);
                }

                List<Object> readModels = new ArrayList<>();
                for (ReadModel readModel : context.readModels()) {
                    List<Object> columns = new ArrayList<>();
                    for (Field column : readModel.columns().fields()) {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("name", Naming.memberName(column.name()));
                        entry.put("type", column.jsonType());
                        entry.put("identity", column.identity());
                        columns.add(entry);
                    }

                    String listPath = null;
                    for (Query query : context.queries()) {
                        if (query.readModel().equals(readModel.name()) && !query.isGet()) {
                            listPath = base + "/" + query.name();
                            break;
                        }
                    }

                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("name", readModel.name());
                    entry.put("columns", columns);
                    entry.put("listPath", listPath);
                    entry.put("stateMachine", stateMachine(context, readModel));
                    readModels.add(entry);
                }

                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", context.name());
                entry.put("commands", commands);
                entry.put("queries", queries);
                entry.put("readModels", readModels);
                contexts.add(entry);
            }

            Map<String, Object> catalog = new LinkedHashMap<>();
            catalog.put("domain", model.domain());
            catalog.put("contexts", contexts);
            return catalog;
        }

        /** Attached when the read model carries a {@code status} column, so a console can show the lifecycle. */
        private static Object stateMachine(BoundedContext context, ReadModel readModel) {
            Aggregate projected = null;
            for (ReadModel.Projection projection : readModel.projections()) {
                projected = aggregateOf(context, projection.aggregate());
                if (projected != null) {
                    break;
                }
            }
            if (projected == null || projected.stateMachine() == null || !readModel.columns().has("status")) {
                return null;
            }

            StateMachine machine = projected.stateMachine();
            List<Object> admits = new ArrayList<>();
            for (StateMachine.Admit admit : machine.admits()) {
                Command command = projected.command(admit.command());
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("command", admit.command());
                entry.put("from", admit.from());
                entry.put("when", admit.when());
                entry.put(
                        "to",
                        command == null || command.primaryEvent() == null
                                ? null
                                : machine.transitionTarget(command.primaryEvent()));
                admits.add(entry);
            }

            List<Object> states = new ArrayList<>();
            for (StateMachine.State state : machine.states()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", state.name());
                entry.put("final", state.isFinal());
                states.add(entry);
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("statusColumn", "status");
            entry.put("initial", machine.initial());
            entry.put("states", states);
            entry.put("admits", admits);
            return entry;
        }
    }
}
