package io.github.rsweet.esdm2opencqrs.adapter.opencqrs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.rsweet.esdm2opencqrs.model.DocumentLoader;
import io.github.rsweet.esdm2opencqrs.model.Model;
import io.github.rsweet.esdm2opencqrs.model.ModelFactory;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OpenCqrsPostgresAdapterTest {

    private static Map<String, String> esdb;
    private static Map<String, String> postgres;

    @BeforeAll
    static void generateBothTargets() {
        Model model = ModelFactory.create(DocumentLoader.loadDirectory(Path.of("examples/todo/model")));
        esdb = new OpenCqrsAdapter().generate(model, new LinkedHashMap<>()).files();
        postgres = new OpenCqrsPostgresAdapter().generate(model, new LinkedHashMap<>()).files();
    }

    /**
     * The point of the store seam: swapping the event store must not touch a single line the model
     * drives. Anything that differs here is store plumbing and nothing else.
     */
    @Test
    void onlyStorePlumbingDiffersBetweenTargets() {
        Set<String> differing = new TreeSet<>();
        for (Map.Entry<String, String> entry : esdb.entrySet()) {
            if (!entry.getValue().equals(postgres.get(entry.getKey()))) {
                differing.add(entry.getKey());
            }
        }

        assertThat(differing)
                .containsExactly(
                        ".env.example",
                        "Dockerfile",
                        "README.md",
                        "compose.yaml",
                        "pom.xml",
                        "src/main/java/app/todo/dev/DevController.java",
                        "src/main/resources/application.properties");
    }

    @Test
    void postgresAddsItsOwnStoreAndNothingElse() {
        Set<String> added = new TreeSet<>(postgres.keySet());
        added.removeAll(esdb.keySet());

        assertThat(added)
                .containsExactly(
                        ".m2-overlay/.keep",
                        "src/main/java/app/todo/config/EventStoreConfiguration.java",
                        "src/main/java/app/todo/store/PostgresEventStoreClient.java",
                        "src/main/resources/schema.sql");
        assertThat(esdb.keySet()).allMatch(postgres::containsKey);
    }

    @Test
    void theStoreClientIsBoundThroughTheFrameworkInterface() {
        assertThat(postgres.get("src/main/java/app/todo/config/EventStoreConfiguration.java"))
                .contains("import com.opencqrs.esdb.client.EventStoreClient;")
                .contains("public EventStoreClient eventStoreClient(DataSource dataSource, ObjectMapper objectMapper)");
        assertThat(postgres.get("src/main/java/app/todo/store/PostgresEventStoreClient.java"))
                .contains("public class PostgresEventStoreClient implements EventStoreClient");
    }

    /** A lost race on (subject, playhead) has to reach the framework as a concurrency conflict. */
    @Test
    void concurrencyConflictsSurfaceAsTheFrameworkExpects() {
        assertThat(postgres.get("src/main/java/app/todo/store/PostgresEventStoreClient.java"))
                .contains("UNIQUE_VIOLATION.equals(e.getSQLState())")
                .contains("new ClientException.HttpException.HttpClientException(message, 409)");
        assertThat(postgres.get("src/main/resources/schema.sql"))
                .contains("CONSTRAINT eventstore_subject_playhead UNIQUE (subject, playhead)");
    }

    /** Unlike EventSourcingDB, this store has a real per-subject sequence, so the 0004 row carries it. */
    @Test
    void theEventWindowReportsARealPlayhead() {
        assertThat(postgres.get("src/main/java/app/todo/dev/DevController.java"))
                .contains("row.put(\"playhead\", rows.getLong(\"playhead\"));");
        assertThat(esdb.get("src/main/java/app/todo/dev/DevController.java")).contains("row.put(\"playhead\", null);");
    }

    /**
     * The store seam needs an OpenCQRS build that publishes the client interface. Pointing at a fork
     * is meant to be a coordinate change and nothing else, so check that it renders a resolvable pom.
     */
    @Test
    void forkCoordinatesRenderAResolvableRepository() {
        String forked = PostgresBootstrap.pom("todo", "com.github.acme.opencqrs", "esdm-2.0.0-1", "https://jitpack.io");

        assertThat(forked)
                .contains("<groupId>com.github.acme.opencqrs</groupId>")
                .contains("<opencqrs.version>esdm-2.0.0-1</opencqrs.version>")
                .contains("<url>https://jitpack.io</url>")
                .doesNotContain("<groupId>com.opencqrs</groupId>");
        assertThatCode(() -> parse(forked)).doesNotThrowAnyException();
    }

    @Test
    void withoutAForkThePomStaysCentralOnly() {
        String central = PostgresBootstrap.pom("todo", "com.opencqrs", "2.0.0", "");

        assertThat(central).doesNotContain("<repositories>");
        assertThatCode(() -> parse(central)).doesNotThrowAnyException();
    }

    private static void parse(String pom) throws Exception {
        javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(pom.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void theEsdbAutoConfigurationIsExcludedSoTheAppSuppliesItsOwnClient() {
        assertThat(postgres.get("pom.xml"))
                .contains("<artifactId>esdb-client-spring-boot-starter</artifactId>")
                .contains("<exclusions>")
                .contains("<artifactId>postgresql</artifactId>");
    }
}
