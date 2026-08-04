package app.todo.write.tasks.task;

import app.todo.support.IllegalTransitionException;
import app.todo.write.tasks.task.commands.AddTaskCommand;
import app.todo.write.tasks.task.commands.DeleteTaskCommand;
import app.todo.write.tasks.task.commands.RenameTaskCommand;
import app.todo.write.tasks.task.commands.SetCompletionCommand;
import app.todo.write.tasks.task.events.TaskAddedEvent;
import app.todo.write.tasks.task.events.TaskCompletionChangedEvent;
import app.todo.write.tasks.task.events.TaskDeletedEvent;
import app.todo.write.tasks.task.events.TaskRenamedEvent;
import com.opencqrs.framework.command.CommandEventPublisher;
import com.opencqrs.framework.command.CommandHandlerConfiguration;
import com.opencqrs.framework.command.CommandHandling;
import com.opencqrs.framework.command.StateRebuilding;
import java.util.Map;
import java.util.Set;

/** Decide (command handling) and evolve (state rebuilding) for the {@code task} aggregate. */
@CommandHandlerConfiguration
public class TaskHandlers {

    @CommandHandling
    public String addTask(
            AddTaskCommand command, Map<String, ?> metaData, CommandEventPublisher<TaskState> publisher) {
        publisher.publish(new TaskAddedEvent(command.id(), command.title()), metaData);
        return command.id();
    }

    @CommandHandling
    public String renameTask(
            TaskState state,
            RenameTaskCommand command,
            Map<String, ?> metaData,
            CommandEventPublisher<TaskState> publisher) {
        admit("rename-task", state, Set.of("open"));
        publisher.publish(new TaskRenamedEvent(command.id(), command.title()), metaData);
        return command.id();
    }

    @CommandHandling
    public String setCompletion(
            TaskState state,
            SetCompletionCommand command,
            Map<String, ?> metaData,
            CommandEventPublisher<TaskState> publisher) {
        admit("set-completion", state, Set.of("open"));
        publisher.publish(new TaskCompletionChangedEvent(command.id(), command.completed()), metaData);
        return command.id();
    }

    @CommandHandling
    public String deleteTask(
            TaskState state,
            DeleteTaskCommand command,
            Map<String, ?> metaData,
            CommandEventPublisher<TaskState> publisher) {
        admit("delete-task", state, Set.of("open"));
        publisher.publish(new TaskDeletedEvent(command.id(), state.title()), metaData);
        return command.id();
    }

    @StateRebuilding
    public TaskState onTaskAdded(TaskAddedEvent event, TaskState state) {
        return new TaskState(event.id(), event.title(), false, "open");
    }

    @StateRebuilding
    public TaskState onTaskRenamed(TaskRenamedEvent event, TaskState state) {
        return new TaskState(state.id(), event.title(), state.completed(), state.status());
    }

    @StateRebuilding
    public TaskState onTaskCompletionChanged(TaskCompletionChangedEvent event, TaskState state) {
        return new TaskState(state.id(), state.title(), event.completed(), state.status());
    }

    @StateRebuilding
    public TaskState onTaskDeleted(TaskDeletedEvent event, TaskState state) {
        return new TaskState(state.id(), state.title(), state.completed(), "deleted");
    }

    private static void admit(String command, TaskState state, Set<String> from) {
        String status = state == null || state.status() == null ? "undefined" : state.status();
        if (!from.contains(status)) {
            throw new IllegalTransitionException(command, status);
        }
    }
}
