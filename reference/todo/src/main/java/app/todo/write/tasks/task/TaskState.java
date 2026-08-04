package app.todo.write.tasks.task;

/** Write model for the {@code task} aggregate. {@code status} carries the 0001 state machine position. */
public record TaskState(String id, String title, Boolean completed, String status) {}
