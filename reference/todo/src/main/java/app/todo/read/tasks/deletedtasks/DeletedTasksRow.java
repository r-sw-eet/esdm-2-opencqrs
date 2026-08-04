package app.todo.read.tasks.deletedtasks;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "rm_deleted_tasks")
public record DeletedTasksRow(@Id String id, String title) {}
