package app.todo.read.tasks.tasks;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Row keys must match the {@code columns[].name} of the {@code tasks} read model in the 0004 catalog. */
@Document(collection = "rm_tasks")
public record TasksRow(@Id String id, String title, Boolean completed) {}
