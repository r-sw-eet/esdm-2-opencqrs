package app.todo.read.tasks.deletedtasks;

import app.todo.write.tasks.task.events.TaskDeletedEvent;
import com.opencqrs.framework.eventhandler.EventHandling;
import org.springframework.stereotype.Component;

/** Second read model off the same log: an archive fed only by {@code task-deleted}. */
@Component
public class DeletedTasksProjector {

    private final DeletedTasksRepository repository;

    public DeletedTasksProjector(DeletedTasksRepository repository) {
        this.repository = repository;
    }

    @EventHandling("deleted-tasks")
    public void onTaskDeleted(TaskDeletedEvent event) {
        repository.save(new DeletedTasksRow(event.id(), event.title()));
    }
}
