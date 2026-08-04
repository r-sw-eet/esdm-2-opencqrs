package io.github.rsweet.esdm2opencqrs.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ObservationsTest {

    @Test
    void masksCapturedValuesWhereverTheyAppear() {
        Map<String, String> captures = Map.of("T1", "abc-123");
        Object masked = Observations.mask(Map.of("id", "abc-123", "title", "untouched"), captures);
        assertThat(Observations.canonical(masked)).isEqualTo("{\"id\":\"«T1»\",\"title\":\"untouched\"}");
    }

    @Test
    void camelizesSnakeKeysOnly() {
        assertThat(Observations.camelize("aggregate_id")).isEqualTo("aggregateId");
        assertThat(Observations.camelize("recorded_on")).isEqualTo("recordedOn");
        assertThat(Observations.camelize("playhead")).isEqualTo("playhead");
    }

    @Test
    void sortsListBodiesByCanonicalJson() {
        Object sorted = Observations.sortIfList(List.of(Map.of("id", "b"), Map.of("id", "a")));
        assertThat(Observations.canonical(sorted)).isEqualTo("[{\"id\":\"a\"},{\"id\":\"b\"}]");
    }

    @Test
    void canonicalJsonSortsKeysLexicographically() {
        Map<String, Object> unordered = new LinkedHashMap<>();
        unordered.put("z", 1);
        unordered.put("a", 2);
        assertThat(Observations.canonical(unordered)).isEqualTo("{\"a\":2,\"z\":1}");
    }

    @Test
    void reducesTheEventTypeToItsLastSegment() {
        assertThat(Observations.eventLabel("todo.task.task-completion-changed")).isEqualTo("task-completion-changed");
        assertThat(Observations.eventLabel("Todo.Task.Task_Added")).isEqualTo("task-added");
    }

    /** Store id and timestamp are per-stack and must be dropped; window order is preserved. */
    @Test
    void normalizesEventRowsToTheUniformShape() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "4");
        row.put("aggregate", "Task");
        row.put("aggregate_id", "abc-123");
        row.put("playhead", null);
        row.put("event", "todo.task.task-added");
        row.put("payload", Map.of("id", "abc-123", "title", "Buy milk"));
        row.put("recorded_on", "2026-08-04T11:58:48Z");

        List<Object> rows = Observations.normalizeEventRows(List.of(row), Map.of("T1", "abc-123"));

        assertThat(Observations.canonical(rows))
                .isEqualTo("[{\"aggregate\":\"task\",\"aggregateId\":\"«T1»\",\"event\":\"task-added\","
                        + "\"payload\":{\"id\":\"«T1»\",\"title\":\"Buy milk\"},\"playhead\":null}]");
    }

    @Test
    void flattensBodiesToFieldPaths() {
        Map<String, Object> body = Map.of("details", Map.of("errorCode", "ILLEGAL_TRANSITION"));
        assertThat(Observations.flatten("body", body)).containsEntry("body.details.errorCode", "ILLEGAL_TRANSITION");
        assertThat(Observations.flatten("body", List.of(Map.of("id", "a"))))
                .containsEntry("body[0].id", "a");
    }
}
