package app.todo.dev;

import com.opencqrs.esdb.client.EsdbClient;
import com.opencqrs.esdb.client.Event;
import com.opencqrs.esdb.client.Option;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev-only window onto the app for an external domain console (0004): the model catalog, the
 * authoring BPMN and the raw event stream. Not part of the domain API - never expose it in production.
 */
@RestController
public class DevController {

    private static final int WINDOW = 50;

    private final EsdbClient client;

    public DevController(EsdbClient client) {
        this.client = client;
    }

    @GetMapping(value = "/_dev/catalog", produces = MediaType.APPLICATION_JSON_VALUE)
    public String catalog() {
        return classpath("catalog.json");
    }

    @GetMapping(value = "/_dev/bpmn", produces = MediaType.APPLICATION_XML_VALUE)
    public String bpmn() {
        return classpath("bpmn.xml");
    }

    /** The newest {@value #WINDOW} events, mapped to the uniform 0004 row shape, newest first. */
    @GetMapping(value = "/_dev/events", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> events() {
        Deque<Event> window = new ArrayDeque<>();
        client.read("/", Set.of(new Option.Recursive()), event -> {
            window.addLast(event);
            if (window.size() > WINDOW) {
                window.removeFirst();
            }
        });

        List<Map<String, Object>> rows = new ArrayList<>(window.size());
        window.descendingIterator().forEachRemaining(event -> rows.add(row(event)));
        return rows;
    }

    private static Map<String, Object> row(Event event) {
        String[] segments = event.subject().split("/");
        Object payload = event.data() == null ? null : event.data().get("payload");

        // EventSourcingDB has no per-subject sequence, so playhead stays null (registered C4 divergence).
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", event.id());
        row.put("aggregate", segments.length > 1 ? segments[1] : "");
        row.put("aggregate_id", segments.length > 0 ? segments[segments.length - 1] : "");
        row.put("playhead", null);
        row.put("event", event.type());
        row.put("payload", payload);
        row.put("recorded_on", event.time() == null ? null : event.time().toString());
        return row;
    }

    private static String classpath(String name) {
        try {
            return new ClassPathResource(name).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + name, e);
        }
    }
}
