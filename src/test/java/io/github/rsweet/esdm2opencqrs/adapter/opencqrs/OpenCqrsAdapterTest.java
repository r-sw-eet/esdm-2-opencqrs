package io.github.rsweet.esdm2opencqrs.adapter.opencqrs;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rsweet.esdm2opencqrs.adapter.GeneratedProject;
import io.github.rsweet.esdm2opencqrs.model.DocumentLoader;
import io.github.rsweet.esdm2opencqrs.model.Model;
import io.github.rsweet.esdm2opencqrs.model.ModelFactory;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OpenCqrsAdapterTest {

    private static Map<String, String> files;

    @BeforeAll
    static void generateTodo() {
        Model model = ModelFactory.create(DocumentLoader.loadDirectory(Path.of("examples/todo/model")));
        GeneratedProject project = new OpenCqrsAdapter().generate(model, new java.util.LinkedHashMap<>(Map.of()));
        files = project.files();
    }

    private static String file(String path) {
        assertThat(files).containsKey(path);
        return files.get(path);
    }

    @Test
    void emitsTheExpectedTree() {
        assertThat(files)
                .containsKeys(
                        "pom.xml",
                        "Dockerfile",
                        "compose.yaml",
                        "src/main/resources/application.properties",
                        "src/main/resources/catalog.json",
                        "src/main/java/app/todo/Application.java",
                        "src/main/java/app/todo/write/tasks/task/TaskHandlers.java",
                        "src/main/java/app/todo/read/tasks/deletedtasks/DeletedTasksProjector.java",
                        "src/test/java/app/todo/write/tasks/task/TaskLifecycleTest.java");
    }

    /** Wire conformance: the family event type and subject scheme are not negotiable. */
    @Test
    void emitsTheFamilyEventTypesAndSubject() {
        assertThat(file("src/main/java/app/todo/config/EventTypeConfiguration.java"))
                .contains("types.put(\"todo.task.task-added\", TaskAddedEvent.class);")
                .contains("types.put(\"todo.task.task-completion-changed\", TaskCompletionChangedEvent.class);");
        assertThat(file("src/main/java/app/todo/write/tasks/task/commands/AddTaskCommand.java"))
                .contains("return \"/task/\" + id;");
    }

    /** Store interchange depends on the meta key being nimbusMeta, not OpenCQRS' default metadata. */
    @Test
    void shipsTheNimbusEnvelopeMarshaller() {
        assertThat(file("src/main/java/app/todo/config/NimbusEventDataMarshaller.java"))
                .contains("return Map.of(\"payload\", payload, \"nimbusMeta\", metaData);");
        assertThat(file("src/main/java/app/todo/config/EventTypeConfiguration.java"))
                .contains("return new EventSource(\"https://esdm-extensions.io/todo\");");
    }

    @Test
    void createCommandsRequireAPristineSubject() {
        assertThat(file("src/main/java/app/todo/write/tasks/task/commands/AddTaskCommand.java"))
                .contains("return SubjectCondition.PRISTINE;");
    }

    /**
     * A guarded mutate command must not carry EXISTS: the 0001 guard has to answer first, so an
     * unknown subject is a 409 illegal transition rather than a 404.
     */
    @Test
    void guardedMutateCommandsLeaveTheSubjectConditionOpen() {
        assertThat(file("src/main/java/app/todo/write/tasks/task/commands/RenameTaskCommand.java"))
                .contains("return SubjectCondition.NONE;");
    }

    @Test
    void appliesStateMachineTransitionsWhenRebuildingState() {
        assertThat(file("src/main/java/app/todo/write/tasks/task/TaskHandlers.java"))
                .contains("admit(\"rename-task\", state, java.util.Set.of(\"open\"));")
                .contains("return new TaskState(event.id(), event.title(), state.completed(), \"deleted\");");
    }

    @Test
    void projectsTheAnchorEventAsAnInsertAndTheDeleteAsARemoval() {
        String projector = file("src/main/java/app/todo/read/tasks/tasks/TasksProjector.java");
        assertThat(projector).contains("repository.save(new TasksRow(event.id(), event.title(), false));");
        assertThat(projector).contains("repository.deleteById(event.id());");

        // Same delete event, different read model: here it is the anchor, so it inserts.
        assertThat(file("src/main/java/app/todo/read/tasks/deletedtasks/DeletedTasksProjector.java"))
                .contains("repository.save(new DeletedTasksRow(event.id(), event.title()));");
    }

    /** 0004 rule: the row keys a finder returns must match the catalog's column names. */
    @Test
    void catalogColumnsMatchTheEmittedRow() {
        assertThat(file("src/main/resources/catalog.json"))
                .contains("\"domain\": \"todo\"")
                .contains("\"listPath\": \"/tasks/list-tasks\"")
                .contains("\"path\": \"/tasks/add-task\"");
        assertThat(file("src/main/java/app/todo/read/tasks/tasks/TasksRow.java"))
                .contains("@Id String id, String title, Boolean completed");
    }

    @Test
    void emitsOneTestPerScenario() {
        String test = file("src/test/java/app/todo/write/tasks/task/TaskLifecycleTest.java");
        assertThat(test)
                .contains("void addATask(")
                .contains("void rejectRenamingADeletedTask(")
                .contains(".throwing(DomainRuleException.class);");
    }

    @Test
    void usesTheSpringBootFourMongoProperties() {
        assertThat(file("src/main/resources/application.properties"))
                .contains("spring.mongodb.uri=")
                .doesNotContain("spring.data.mongodb.uri=");
    }
}
