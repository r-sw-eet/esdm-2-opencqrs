package app.todo.read.tasks.tasks;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface TasksRepository extends MongoRepository<TasksRow, String> {}
