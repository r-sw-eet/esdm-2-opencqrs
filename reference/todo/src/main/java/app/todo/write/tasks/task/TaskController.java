package app.todo.write.tasks.task;

import app.todo.write.tasks.task.commands.AddTaskCommand;
import app.todo.write.tasks.task.commands.DeleteTaskCommand;
import app.todo.write.tasks.task.commands.RenameTaskCommand;
import app.todo.write.tasks.task.commands.SetCompletionCommand;
import com.opencqrs.framework.command.CommandRouter;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final CommandRouter commandRouter;

    public TaskController(CommandRouter commandRouter) {
        this.commandRouter = commandRouter;
    }

    public record AddTaskInput(String title) {}

    public record RenameTaskInput(String id, String title) {}

    public record SetCompletionInput(String id, Boolean completed) {}

    public record DeleteTaskInput(String id) {}

    @PostMapping("/add-task")
    public Map<String, Object> addTask(@RequestBody AddTaskInput input, @RequestHeader(name = CORRELATION_HEADER, required = false) String correlationId) {
        String id = commandRouter.send(new AddTaskCommand(UUID.randomUUID().toString(), input.title()), metaData(correlationId));
        return Map.of("id", id);
    }

    @PostMapping("/rename-task")
    public Map<String, Object> renameTask(@RequestBody RenameTaskInput input, @RequestHeader(name = CORRELATION_HEADER, required = false) String correlationId) {
        String id = commandRouter.send(new RenameTaskCommand(input.id(), input.title()), metaData(correlationId));
        return Map.of("id", id);
    }

    @PostMapping("/set-completion")
    public Map<String, Object> setCompletion(@RequestBody SetCompletionInput input, @RequestHeader(name = CORRELATION_HEADER, required = false) String correlationId) {
        String id = commandRouter.send(new SetCompletionCommand(input.id(), input.completed()), metaData(correlationId));
        return Map.of("id", id);
    }

    @PostMapping("/delete-task")
    public Map<String, Object> deleteTask(@RequestBody DeleteTaskInput input, @RequestHeader(name = CORRELATION_HEADER, required = false) String correlationId) {
        String id = commandRouter.send(new DeleteTaskCommand(input.id()), metaData(correlationId));
        return Map.of("id", id);
    }

    private static final String CORRELATION_HEADER = "X-Correlation-ID";

    private static Map<String, Object> metaData(String correlationId) {
        return Map.of("correlationid", correlationId == null ? UUID.randomUUID().toString() : correlationId);
    }
}
