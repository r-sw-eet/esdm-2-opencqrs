package app.todo.read.tasks.deletedtasks;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface DeletedTasksRepository extends MongoRepository<DeletedTasksRow, String> {}
