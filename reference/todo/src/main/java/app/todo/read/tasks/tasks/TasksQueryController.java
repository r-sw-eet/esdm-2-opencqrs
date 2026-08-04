package app.todo.read.tasks.tasks;

import static app.todo.support.ApiError.details;

import app.todo.support.ApiError;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tasks")
public class TasksQueryController {

    private final TasksRepository repository;

    public TasksQueryController(TasksRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/list-tasks")
    public List<TasksRow> listTasks() {
        return repository.findAll();
    }

    @GetMapping("/get-task")
    public ResponseEntity<?> getTask(@RequestParam("id") String id) {
        return repository
                .findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiError(
                                "NOT_FOUND",
                                "Tasks not found",
                                details(
                                        "errorCode",
                                        "TASKS_NOT_FOUND",
                                        "reason",
                                        "Could not find Tasks matching the given filter"))));
    }
}
