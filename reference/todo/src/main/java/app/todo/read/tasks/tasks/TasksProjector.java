package app.todo.read.tasks.tasks;

import app.todo.write.tasks.task.events.TaskAddedEvent;
import app.todo.write.tasks.task.events.TaskCompletionChangedEvent;
import app.todo.write.tasks.task.events.TaskDeletedEvent;
import app.todo.write.tasks.task.events.TaskRenamedEvent;
import com.opencqrs.framework.eventhandler.EventHandling;
import org.springframework.stereotype.Component;

@Component
public class TasksProjector {

    private static final String GROUP = "tasks";

    private final TasksRepository repository;

    public TasksProjector(TasksRepository repository) {
        this.repository = repository;
    }

    @EventHandling(GROUP)
    public void onTaskAdded(TaskAddedEvent event) {
        repository.save(new TasksRow(event.id(), event.title(), false));
    }

    @EventHandling(GROUP)
    public void onTaskRenamed(TaskRenamedEvent event) {
        repository.findById(event.id())
                .ifPresent(row -> repository.save(new TasksRow(row.id(), event.title(), row.completed())));
    }

    @EventHandling(GROUP)
    public void onTaskCompletionChanged(TaskCompletionChangedEvent event) {
        repository.findById(event.id())
                .ifPresent(row -> repository.save(new TasksRow(row.id(), row.title(), event.completed())));
    }

    @EventHandling(GROUP)
    public void onTaskDeleted(TaskDeletedEvent event) {
        repository.deleteById(event.id());
    }
}
