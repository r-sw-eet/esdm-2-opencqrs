package app.todo;

import app.todo.support.IllegalTransitionException;
import app.todo.write.tasks.task.commands.AddTaskCommand;
import app.todo.write.tasks.task.commands.DeleteTaskCommand;
import app.todo.write.tasks.task.commands.RenameTaskCommand;
import app.todo.write.tasks.task.commands.SetCompletionCommand;
import app.todo.write.tasks.task.events.TaskAddedEvent;
import app.todo.write.tasks.task.events.TaskCompletionChangedEvent;
import app.todo.write.tasks.task.events.TaskDeletedEvent;
import app.todo.write.tasks.task.events.TaskRenamedEvent;
import com.opencqrs.framework.command.CommandHandlingTest;
import com.opencqrs.framework.command.CommandHandlingTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** One test per scenario of the {@code task-lifecycle} GWT feature. */
@CommandHandlingTest
class TaskLifecycleTest {

    private static final String TASK_ID = "11111111-1111-1111-1111-111111111111";

    @Test
    void addATask(@Autowired CommandHandlingTestFixture<AddTaskCommand> fixture) {
        fixture.given()
                .nothing()
                .when(new AddTaskCommand(TASK_ID, "Buy milk"))
                .succeeds()
                .allEvents()
                .exactly(new TaskAddedEvent(TASK_ID, "Buy milk"));
    }

    @Test
    void renameAnExistingTask(@Autowired CommandHandlingTestFixture<RenameTaskCommand> fixture) {
        fixture.given()
                .events(new TaskAddedEvent(TASK_ID, "Buy milk"))
                .when(new RenameTaskCommand(TASK_ID, "Buy oat milk"))
                .succeeds()
                .allEvents()
                .exactly(new TaskRenamedEvent(TASK_ID, "Buy oat milk"));
    }

    @Test
    void completeATask(@Autowired CommandHandlingTestFixture<SetCompletionCommand> fixture) {
        fixture.given()
                .events(new TaskAddedEvent(TASK_ID, "Buy milk"))
                .when(new SetCompletionCommand(TASK_ID, true))
                .succeeds()
                .allEvents()
                .exactly(new TaskCompletionChangedEvent(TASK_ID, true));
    }

    @Test
    void deleteATask(@Autowired CommandHandlingTestFixture<DeleteTaskCommand> fixture) {
        fixture.given()
                .events(new TaskAddedEvent(TASK_ID, "Buy milk"))
                .when(new DeleteTaskCommand(TASK_ID))
                .succeeds()
                .allEvents()
                .exactly(new TaskDeletedEvent(TASK_ID, "Buy milk"));
    }

    @Test
    void rejectRenamingADeletedTask(@Autowired CommandHandlingTestFixture<RenameTaskCommand> fixture) {
        fixture.given()
                .events(new TaskAddedEvent(TASK_ID, "Buy milk"), new TaskDeletedEvent(TASK_ID, "Buy milk"))
                .when(new RenameTaskCommand(TASK_ID, "Hacked"))
                .fails()
                .throwing(IllegalTransitionException.class);
    }
}
