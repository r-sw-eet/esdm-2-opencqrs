package app.todo.config;

import com.opencqrs.framework.eventhandler.progress.Progress;
import com.opencqrs.framework.eventhandler.progress.ProgressTracker;
import java.util.function.Supplier;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Durable {@code @EventHandling} progress in MongoDB. The shipped trackers are JDBC (would add a
 * third datastore) or in-memory (replays the whole stream on every restart); the read side already
 * runs on Mongo, so progress lives there too.
 */
@Component("mongoProgressTracker")
public class MongoProgressTracker implements ProgressTracker {

    static final String COLLECTION = "event_handling_progress";

    private final MongoTemplate mongoTemplate;

    public MongoProgressTracker(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Progress current(String group, long partition) {
        ProgressDocument document = mongoTemplate.findById(key(group, partition), ProgressDocument.class, COLLECTION);
        if (document == null || document.eventId() == null) {
            return new Progress.None();
        }
        return new Progress.Success(document.eventId());
    }

    @Override
    public void proceed(String group, long partition, Supplier<Progress> execution) {
        Progress progress = execution.get();
        if (progress instanceof Progress.Success success) {
            mongoTemplate.save(new ProgressDocument(key(group, partition), success.id()), COLLECTION);
        }
    }

    private static String key(String group, long partition) {
        return group + ":" + partition;
    }

    record ProgressDocument(@Id String id, String eventId) {}
}
