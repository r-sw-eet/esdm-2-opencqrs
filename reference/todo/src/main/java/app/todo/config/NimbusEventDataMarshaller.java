package app.todo.config;

import com.opencqrs.framework.CqrsFrameworkException;
import com.opencqrs.framework.serialization.EventData;
import com.opencqrs.framework.serialization.EventDataMarshaller;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes the ESDM family event envelope: {@code data = { payload, nimbusMeta }}. OpenCQRS' own
 * marshaller names the meta key {@code metadata}; the sibling generators all write {@code nimbusMeta},
 * and store interchange depends on that key matching.
 */
public class NimbusEventDataMarshaller implements EventDataMarshaller {

    private final ObjectMapper objectMapper;

    public NimbusEventDataMarshaller(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public <E> Map<String, ?> serialize(EventData<E> data) {
        try {
            Map<?, ?> payload = objectMapper.convertValue(data.payload(), Map.class);
            Map<?, ?> metaData = objectMapper.convertValue(data.metaData(), Map.class);
            return Map.of("payload", payload, "nimbusMeta", metaData);
        } catch (JacksonException e) {
            throw new CqrsFrameworkException.NonTransientException("failed to serialize: " + data, e);
        }
    }

    @Override
    public <E> EventData<E> deserialize(Map<String, ?> json, Class<E> clazz) {
        try {
            NimbusData<E> deserialized = objectMapper.convertValue(
                    json, objectMapper.getTypeFactory().constructParametricType(NimbusData.class, clazz));
            return new EventData<>(
                    deserialized.nimbusMeta() == null ? Map.of() : deserialized.nimbusMeta(), deserialized.payload());
        } catch (JacksonException e) {
            throw new CqrsFrameworkException.NonTransientException("failed to deserialize: " + json, e);
        }
    }

    record NimbusData<E>(Map<String, ?> nimbusMeta, E payload) {}
}
