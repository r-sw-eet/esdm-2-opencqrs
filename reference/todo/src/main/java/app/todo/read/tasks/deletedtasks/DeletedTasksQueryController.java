package app.todo.read.tasks.deletedtasks;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tasks")
public class DeletedTasksQueryController {

    private final DeletedTasksRepository repository;

    public DeletedTasksQueryController(DeletedTasksRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/list-deleted-tasks")
    public List<DeletedTasksRow> listDeletedTasks() {
        return repository.findAll();
    }
}
