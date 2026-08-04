package io.github.rsweet.esdm2opencqrs.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ModelFactoryTest {

    private static Model model;

    @BeforeAll
    static void loadTodo() {
        model = ModelFactory.create(DocumentLoader.loadDirectory(Path.of("examples/todo/model")));
    }

    @Test
    void resolvesTheDomainGraph() {
        assertThat(model.domain()).isEqualTo("todo");
        assertThat(model.boundedContexts()).singleElement().satisfies(context -> {
            assertThat(context.name()).isEqualTo("tasks");
            assertThat(context.aggregates()).hasSize(1);
            assertThat(context.readModels()).extracting(ReadModel::name).containsExactly("tasks", "deleted-tasks");
            assertThat(context.queries()).hasSize(3);
        });
    }

    @Test
    void derivesLifecycleFromTheCommandVerb() {
        Aggregate task = model.aggregate("tasks", "task");
        assertThat(task.command("add-task").lifecycle()).isEqualTo(Lifecycle.CREATE);
        assertThat(task.command("rename-task").lifecycle()).isEqualTo(Lifecycle.MUTATE);
        assertThat(task.command("delete-task").lifecycle()).isEqualTo(Lifecycle.DELETE);
    }

    @Test
    void propagatesCommandLifecycleOntoPublishedEvents() {
        Aggregate task = model.aggregate("tasks", "task");
        assertThat(task.event("task-added").lifecycle()).isEqualTo(Lifecycle.CREATE);
        assertThat(task.event("task-deleted").lifecycle()).isEqualTo(Lifecycle.DELETE);
    }

    @Test
    void buildsTheFamilyEventType() {
        assertThat(model.aggregate("tasks", "task").event("task-completion-changed").type())
                .isEqualTo("todo.task.task-completion-changed");
    }

    /**
     * The state machine's {@code on:} key is a YAML 1.1 boolean. Resolving it as one would silently
     * drop every transition and leave aggregates stuck in their initial state.
     */
    @Test
    void readsStateMachineTransitionsDespiteTheYamlOnKeyword() {
        StateMachine machine = model.aggregate("tasks", "task").stateMachine();
        assertThat(machine).isNotNull();
        assertThat(machine.initial()).isEqualTo("open");
        assertThat(machine.transitionTarget("task-added")).isEqualTo("open");
        assertThat(machine.transitionTarget("task-deleted")).isEqualTo("deleted");
        assertThat(machine.admitFor("rename-task").from()).containsExactly("open");
    }

    @Test
    void marksTheIdentityFieldOnAggregateState() {
        Aggregate task = model.aggregate("tasks", "task");
        assertThat(task.identityField()).isEqualTo("id");
        assertThat(task.state().field("id").identity()).isTrue();
        assertThat(task.state().field("title").identity()).isFalse();
        assertThat(task.nonIdentityState()).extracting(Field::name).containsExactly("title", "completed");
    }

    @Test
    void parsesGivenWhenThenScenarios() {
        List<Feature> features = model.featuresFor("tasks", "task");
        assertThat(features).singleElement().satisfies(feature -> {
            assertThat(feature.scenarios()).hasSize(5);
            assertThat(feature.scenarios().stream().filter(Feature.Scenario::isRejection).count())
                    .isEqualTo(1);
        });
    }

    @Test
    void keepsSchemaDefaults() {
        Field completed = model.aggregate("tasks", "task").state().field("completed");
        assertThat(completed.jsonType()).isEqualTo("boolean");
        assertThat(completed.hasDefault()).isTrue();
        assertThat(completed.defaultValue()).isEqualTo(false);
    }
}
