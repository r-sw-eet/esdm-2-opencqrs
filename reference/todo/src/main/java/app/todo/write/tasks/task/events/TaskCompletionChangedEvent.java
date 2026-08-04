package app.todo.write.tasks.task.events;

public record TaskCompletionChangedEvent(String id, Boolean completed) {}
