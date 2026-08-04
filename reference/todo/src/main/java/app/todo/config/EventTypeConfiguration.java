package app.todo.config;

import app.todo.write.tasks.task.events.TaskAddedEvent;
import app.todo.write.tasks.task.events.TaskCompletionChangedEvent;
import app.todo.write.tasks.task.events.TaskDeletedEvent;
import app.todo.write.tasks.task.events.TaskRenamedEvent;
import com.opencqrs.framework.persistence.EventSource;
import com.opencqrs.framework.serialization.EventDataMarshaller;
import com.opencqrs.framework.types.EventTypeResolver;
import com.opencqrs.framework.types.PreconfiguredAssignableClassEventTypeResolver;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Event wire types and envelope. The resolver map must be complete: once a custom
 * {@link EventTypeResolver} is defined there is no class-name fallback.
 */
@Configuration
public class EventTypeConfiguration {

    @Bean
    public EventTypeResolver eventTypeResolver() {
        return new PreconfiguredAssignableClassEventTypeResolver(Map.of(
                "todo.task.task-added", TaskAddedEvent.class,
                "todo.task.task-renamed", TaskRenamedEvent.class,
                "todo.task.task-completion-changed", TaskCompletionChangedEvent.class,
                "todo.task.task-deleted", TaskDeletedEvent.class));
    }

    @Bean
    public EventDataMarshaller eventDataMarshaller(ObjectMapper objectMapper) {
        return new NimbusEventDataMarshaller(objectMapper);
    }

    /** The family's CloudEvents source; OpenCQRS would default to {@code tag://<application name>}. */
    @Bean
    public EventSource eventSource() {
        return new EventSource("https://esdm-extensions.io/todo");
    }
}
