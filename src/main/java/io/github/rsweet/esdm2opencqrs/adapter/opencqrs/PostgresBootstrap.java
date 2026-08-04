package io.github.rsweet.esdm2opencqrs.adapter.opencqrs;

/**
 * The Postgres-backed variant of the stack-fixed files. Everything the model drives is identical to
 * the EventSourcingDB target; only the event store behind {@code EventStoreClient} differs.
 */
final class PostgresBootstrap {

    private PostgresBootstrap() {}

    /**
     * @param group the OpenCQRS group id; a fork published through JitPack serves the modules under
     *     {@code com.github.<owner>.<repo>} rather than {@code com.opencqrs}
     * @param repository where to resolve that group from, or empty for Maven Central only
     */
    static String pom(String appName, String group, String opencqrsVersion, String repository) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>

                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>{{boot}}</version>
                        <relativePath/>
                    </parent>

                    <groupId>app</groupId>
                    <artifactId>{{app}}</artifactId>
                    <version>{{version}}</version>
                    <name>{{app}}</name>
                    <description>Generated from an ESDM model - Spring Boot + OpenCQRS on PostgreSQL</description>

                    <properties>
                        <java.version>21</java.version>
                        <opencqrs.version>{{opencqrs}}</opencqrs.version>
                    </properties>{{repositories}}

                    <dependencies>
                        <dependency>
                            <groupId>{{group}}</groupId>
                            <artifactId>framework-spring-boot-starter</artifactId>
                            <version>${opencqrs.version}</version>
                            <exclusions>
                                <!-- The EventSourcingDB client is auto-configured by this starter; this app
                                     supplies its own EventStoreClient over PostgreSQL instead. -->
                                <exclusion>
                                    <groupId>{{group}}</groupId>
                                    <artifactId>esdb-client-spring-boot-starter</artifactId>
                                </exclusion>
                            </exclusions>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-data-mongodb</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-jdbc</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.postgresql</groupId>
                            <artifactId>postgresql</artifactId>
                            <scope>runtime</scope>
                        </dependency>

                        <dependency>
                            <groupId>{{group}}</groupId>
                            <artifactId>framework-test</artifactId>
                            <version>${opencqrs.version}</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-test</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>

                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """
                .replace("{{app}}", appName)
                .replace("{{boot}}", Bootstrap.SPRING_BOOT_VERSION)
                .replace("{{group}}", group)
                .replace("{{opencqrs}}", opencqrsVersion)
                .replace("{{repositories}}", repositories(repository))
                .replace("{{version}}", Bootstrap.APP_VERSION);
    }

    /** Rendered at the emitted pom's own indentation, so it is built by hand rather than as a text block. */
    private static String repositories(String repository) {
        if (repository.isEmpty()) {
            return "";
        }
        return "\n\n    <repositories>\n"
                + "        <!-- The event-store seam is not in a Maven Central release yet. -->\n"
                + "        <repository>\n"
                + "            <id>opencqrs-fork</id>\n"
                + "            <url>" + repository + "</url>\n"
                + "        </repository>\n"
                + "    </repositories>";
    }

    /**
     * Same image as the EventSourcingDB target, plus an overlay of the local Maven repository. The
     * OpenCQRS build carrying the {@code EventStoreClient} interface is not on Maven Central, so the
     * builder cannot resolve it; drop the artifacts into {@code .m2-overlay/} to build this app.
     * Once the upstream change lands, delete the overlay and this comment.
     */
    static String dockerfile(String appName) {
        return """
                FROM maven:3.9-eclipse-temurin-21 AS build
                WORKDIR /build
                # Prototype only: the interface-bearing OpenCQRS build is not published yet.
                COPY .m2-overlay/ /root/.m2/repository/
                COPY pom.xml .
                RUN mvn -B -q dependency:go-offline
                COPY src ./src
                RUN mvn -B -q package -DskipTests

                FROM eclipse-temurin:21-jre
                WORKDIR /app
                COPY --from=build /build/target/{{app}}-{{version}}.jar app.jar
                EXPOSE 8080
                ENTRYPOINT ["java", "-jar", "/app/app.jar"]
                """
                .replace("{{app}}", appName)
                .replace("{{version}}", Bootstrap.APP_VERSION);
    }

    static String compose() {
        return """
                # Generated stack: postgres (event store), mongo (read models),
                # api (Spring Boot HTTP + in-process OpenCQRS projections).
                services:
                  postgres:
                    image: postgres:17
                    environment:
                      POSTGRES_DB: app
                      POSTGRES_USER: app
                      POSTGRES_PASSWORD: app
                    ports:
                      - "5433:5432"
                    volumes:
                      - postgres-data:/var/lib/postgresql/data

                  mongo:
                    image: mongo:7
                    ports:
                      - "27018:27017"
                    volumes:
                      - mongo-data:/data/db

                  api:
                    build: .
                    environment:
                      HTTP_PORT: "8080"
                      MONGO_DB: app
                      MONGO_URL: "mongodb://mongo:27017"
                      POSTGRES_URL: "jdbc:postgresql://postgres:5432/app"
                      POSTGRES_USER: app
                      POSTGRES_PASSWORD: app
                    depends_on:
                      - postgres
                      - mongo
                    ports:
                      - "8080:8080"

                # Domain console: this stack serves the 0004 dev contract (/_dev/*) - point the
                # esdm-vue-reader viewer at http://localhost:8080 for commands / read models / events.

                volumes:
                  postgres-data:
                  mongo-data:
                """;
    }

    static String envExample() {
        return """
                HTTP_PORT=8080

                MONGO_DB=app
                MONGO_URL=mongodb://mongo:27017

                POSTGRES_URL=jdbc:postgresql://postgres:5432/app
                POSTGRES_USER=app
                POSTGRES_PASSWORD=app
                """;
    }

    static String applicationProperties(String appName) {
        return """
                spring.application.name={{app}}

                server.port=${HTTP_PORT:8080}

                spring.datasource.url=${POSTGRES_URL:jdbc:postgresql://localhost:5432/app}
                spring.datasource.username=${POSTGRES_USER:app}
                spring.datasource.password=${POSTGRES_PASSWORD:app}
                spring.sql.init.mode=always

                # Spring Boot 4 moved these off spring.data.mongodb.* (deprecated at error level).
                spring.mongodb.uri=${MONGO_URL:mongodb://localhost:27017}
                spring.mongodb.database=${MONGO_DB:app}

                # Durable projection progress in Mongo, so a restart resumes instead of replaying.
                opencqrs.event-handling.standard.progress.tracker-ref=mongoProgressTracker
                opencqrs.event-handling.standard.fetch.subject=/
                opencqrs.event-handling.standard.fetch.recursive=true
                """
                .replace("{{app}}", appName);
    }

    static String schema() {
        return """
                -- Append-only event log. `id` orders the global stream; `playhead` is the per-subject
                -- sequence, and the unique constraint on (subject, playhead) is what makes a concurrent
                -- append to the same subject fail instead of interleaving.
                CREATE TABLE IF NOT EXISTS eventstore (
                    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    source       TEXT        NOT NULL,
                    subject      TEXT        NOT NULL,
                    type         TEXT        NOT NULL,
                    spec_version TEXT        NOT NULL DEFAULT '1.0',
                    data         JSONB       NOT NULL,
                    playhead     BIGINT      NOT NULL,
                    recorded_on  TIMESTAMPTZ NOT NULL DEFAULT now(),
                    CONSTRAINT eventstore_subject_playhead UNIQUE (subject, playhead)
                );

                CREATE INDEX IF NOT EXISTS eventstore_subject_idx ON eventstore (subject, id);
                CREATE INDEX IF NOT EXISTS eventstore_subject_prefix_idx ON eventstore (subject text_pattern_ops);
                """;
    }

    static String eventStoreConfiguration(String basePackage) {
        return """
                package {{pkg}}.config;

                import {{pkg}}.store.PostgresEventStoreClient;
                import com.opencqrs.esdb.client.EventStoreClient;
                import java.time.Duration;
                import javax.sql.DataSource;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;
                import tools.jackson.databind.ObjectMapper;

                /**
                 * Binds the CQRS framework to PostgreSQL. The framework talks to the store through
                 * {@link EventStoreClient} only, so swapping the implementation swaps the event store.
                 */
                @Configuration
                public class EventStoreConfiguration {

                    @Bean
                    public EventStoreClient eventStoreClient(DataSource dataSource, ObjectMapper objectMapper) {
                        return new PostgresEventStoreClient(dataSource, objectMapper, Duration.ofMillis(250));
                    }
                }
                """
                .replace("{{pkg}}", basePackage);
    }

    static String storeClient(String basePackage) {
        return """
                package {{pkg}}.store;

                import com.opencqrs.esdb.client.ClientException;
                import com.opencqrs.esdb.client.Event;
                import com.opencqrs.esdb.client.EventCandidate;
                import com.opencqrs.esdb.client.EventStoreClient;
                import com.opencqrs.esdb.client.Option;
                import com.opencqrs.esdb.client.Precondition;
                import java.sql.Connection;
                import java.sql.PreparedStatement;
                import java.sql.ResultSet;
                import java.sql.SQLException;
                import java.sql.Timestamp;
                import java.time.Duration;
                import java.util.ArrayList;
                import java.util.LinkedHashSet;
                import java.util.List;
                import java.util.Map;
                import java.util.Set;
                import java.util.function.Consumer;
                import javax.sql.DataSource;
                import tools.jackson.databind.ObjectMapper;

                /**
                 * An {@link EventStoreClient} over a single PostgreSQL table, so the app can run on Postgres
                 * instead of EventSourcingDB without any change to command handling, projections or the HTTP
                 * surface. Unlike EventSourcingDB this store keeps a real per-subject {@code playhead}.
                 *
                 * <p>The {@code com.opencqrs.esdb.client} imports are not a mistake: that package holds the
                 * framework's event vocabulary - {@link Event}, {@link Option}, {@link Precondition} - which
                 * every store speaks, whatever database sits behind it.
                 */
                public class PostgresEventStoreClient implements EventStoreClient {

                    private static final String COLUMNS =
                            "id, source, subject, type, spec_version, data, playhead, recorded_on";
                    private static final String CONTENT_TYPE = "application/json";
                    private static final String UNIQUE_VIOLATION = "23505";

                    private final DataSource dataSource;
                    private final ObjectMapper objectMapper;
                    private final Duration pollInterval;

                    public PostgresEventStoreClient(DataSource dataSource, ObjectMapper objectMapper, Duration pollInterval) {
                        this.dataSource = dataSource;
                        this.objectMapper = objectMapper;
                        this.pollInterval = pollInterval;
                    }

                    @Override
                    public List<Event> write(List<EventCandidate> eventCandidates, List<Precondition> preconditions) {
                        try (Connection connection = dataSource.getConnection()) {
                            connection.setAutoCommit(false);
                            try {
                                for (Precondition precondition : preconditions) {
                                    verify(connection, precondition);
                                }
                                List<Event> written = new ArrayList<>(eventCandidates.size());
                                for (EventCandidate candidate : eventCandidates) {
                                    written.add(append(connection, candidate));
                                }
                                connection.commit();
                                return written;
                            } catch (SQLException e) {
                                connection.rollback();
                                // Losing the race on (subject, playhead) is exactly a concurrency conflict.
                                if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
                                    throw violated("concurrent append to the same subject");
                                }
                                throw new ClientException.TransportException("failed to append events", e);
                            } catch (RuntimeException e) {
                                connection.rollback();
                                throw e;
                            }
                        } catch (SQLException e) {
                            throw new ClientException.TransportException("failed to obtain a connection", e);
                        }
                    }

                    @Override
                    public void read(String subject, Set<Option> options, Consumer<Event> eventConsumer) {
                        try (Connection connection = dataSource.getConnection()) {
                            read(connection, subject, options, eventConsumer);
                        } catch (SQLException e) {
                            throw new ClientException.TransportException("failed to read events", e);
                        }
                    }

                    /**
                     * Streams matching events, then keeps polling for later ones until the calling thread is
                     * interrupted - the blocking contract {@code EventHandlingProcessor} drives.
                     */
                    @Override
                    public void observe(String subject, Set<Option> options, Consumer<Event> eventConsumer) {
                        Long cursor = lowerBound(options);
                        Set<Option> base = new LinkedHashSet<>();
                        for (Option option : options) {
                            if (!(option instanceof Option.LowerBoundInclusive)
                                    && !(option instanceof Option.LowerBoundExclusive)) {
                                base.add(option);
                            }
                        }

                        try {
                            while (true) {
                                Set<Option> current = new LinkedHashSet<>(base);
                                if (cursor != null) {
                                    current.add(new Option.LowerBoundExclusive(String.valueOf(cursor)));
                                }

                                List<Event> batch = new ArrayList<>();
                                read(subject, current, batch::add);
                                for (Event event : batch) {
                                    eventConsumer.accept(event);
                                    cursor = Long.parseLong(event.id());
                                }

                                Thread.sleep(pollInterval.toMillis());
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new ClientException.InterruptedException("observation interrupted", e);
                        }
                    }

                    private void read(Connection connection, String subject, Set<Option> options, Consumer<Event> eventConsumer)
                            throws SQLException {
                        List<Object> parameters = new ArrayList<>();
                        String sql = query(connection, subject, options, parameters);

                        try (PreparedStatement statement = connection.prepareStatement(sql)) {
                            for (int i = 0; i < parameters.size(); i++) {
                                statement.setObject(i + 1, parameters.get(i));
                            }
                            try (ResultSet rows = statement.executeQuery()) {
                                while (rows.next()) {
                                    eventConsumer.accept(toEvent(rows));
                                }
                            }
                        }
                    }

                    private String query(Connection connection, String subject, Set<Option> options, List<Object> parameters)
                            throws SQLException {
                        StringBuilder sql = new StringBuilder("SELECT ").append(COLUMNS).append(" FROM eventstore WHERE ");

                        boolean recursive = options.stream().anyMatch(option -> option instanceof Option.Recursive);
                        if (recursive && "/".equals(subject)) {
                            sql.append("TRUE");
                        } else if (recursive) {
                            sql.append("(subject = ? OR subject LIKE ?)");
                            parameters.add(subject);
                            parameters.add(subject + "/%");
                        } else {
                            sql.append("subject = ?");
                            parameters.add(subject);
                        }

                        for (Option option : options) {
                            switch (option) {
                                case Option.LowerBoundInclusive bound -> {
                                    sql.append(" AND id >= ?");
                                    parameters.add(Long.parseLong(bound.id()));
                                }
                                case Option.LowerBoundExclusive bound -> {
                                    sql.append(" AND id > ?");
                                    parameters.add(Long.parseLong(bound.id()));
                                }
                                case Option.UpperBoundInclusive bound -> {
                                    sql.append(" AND id <= ?");
                                    parameters.add(Long.parseLong(bound.id()));
                                }
                                case Option.UpperBoundExclusive bound -> {
                                    sql.append(" AND id < ?");
                                    parameters.add(Long.parseLong(bound.id()));
                                }
                                case Option.FromLatestEvent latest -> {
                                    Long from = latestIdOfType(connection, latest.subject(), latest.type());
                                    if (from != null) {
                                        sql.append(" AND id >= ?");
                                        parameters.add(from);
                                    } else if (latest.ifEventIsMissing() == Option.FromLatestEvent.IfEventIsMissing.READ_NOTHING) {
                                        sql.append(" AND FALSE");
                                    }
                                }
                                case Option.Recursive ignored -> {}
                                case Option.Order ignored -> {}
                            }
                        }

                        boolean descending = options.stream()
                                .anyMatch(option -> option instanceof Option.Order order
                                        && order.type() == Option.Order.Type.ANTICHRONOLOGICAL);
                        sql.append(descending ? " ORDER BY id DESC" : " ORDER BY id ASC");

                        return sql.toString();
                    }

                    private void verify(Connection connection, Precondition precondition) throws SQLException {
                        switch (precondition) {
                            case Precondition.SubjectIsPristine pristine -> {
                                if (count(connection, pristine.subject()) > 0) {
                                    throw violated("subject is not pristine: " + pristine.subject());
                                }
                            }
                            case Precondition.SubjectIsPopulated populated -> {
                                if (count(connection, populated.subject()) == 0) {
                                    throw violated("subject is not populated: " + populated.subject());
                                }
                            }
                            case Precondition.SubjectIsOnEventId onEventId -> {
                                Long latest = latestId(connection, onEventId.subject());
                                if (latest == null || !String.valueOf(latest).equals(onEventId.eventId())) {
                                    throw violated("subject moved on: " + onEventId.subject());
                                }
                            }
                            case Precondition.EventQlQueryIsTrue ignored ->
                                throw new ClientException.InvalidUsageException(
                                        "EventQL preconditions are specific to EventSourcingDB");
                        }
                    }

                    private Event append(Connection connection, EventCandidate candidate) throws SQLException {
                        long playhead = latestPlayhead(connection, candidate.subject()) + 1;

                        try (PreparedStatement statement = connection.prepareStatement(
                                "INSERT INTO eventstore (source, subject, type, spec_version, data, playhead)"
                                        + " VALUES (?, ?, ?, '1.0', ?::jsonb, ?) RETURNING id, recorded_on")) {
                            statement.setString(1, candidate.source());
                            statement.setString(2, candidate.subject());
                            statement.setString(3, candidate.type());
                            statement.setString(4, objectMapper.writeValueAsString(candidate.data()));
                            statement.setLong(5, playhead);

                            try (ResultSet rows = statement.executeQuery()) {
                                rows.next();
                                return new Event(
                                        candidate.source(),
                                        candidate.subject(),
                                        candidate.type(),
                                        candidate.data(),
                                        "1.0",
                                        String.valueOf(rows.getLong("id")),
                                        rows.getTimestamp("recorded_on").toInstant(),
                                        CONTENT_TYPE,
                                        null,
                                        null);
                            }
                        }
                    }

                    @SuppressWarnings("unchecked")
                    private Event toEvent(ResultSet rows) throws SQLException {
                        Timestamp recordedOn = rows.getTimestamp("recorded_on");
                        return new Event(
                                rows.getString("source"),
                                rows.getString("subject"),
                                rows.getString("type"),
                                objectMapper.readValue(rows.getString("data"), Map.class),
                                rows.getString("spec_version"),
                                String.valueOf(rows.getLong("id")),
                                recordedOn == null ? null : recordedOn.toInstant(),
                                CONTENT_TYPE,
                                null,
                                null);
                    }

                    private long count(Connection connection, String subject) throws SQLException {
                        try (PreparedStatement statement =
                                connection.prepareStatement("SELECT count(*) FROM eventstore WHERE subject = ?")) {
                            statement.setString(1, subject);
                            try (ResultSet rows = statement.executeQuery()) {
                                rows.next();
                                return rows.getLong(1);
                            }
                        }
                    }

                    private Long latestId(Connection connection, String subject) throws SQLException {
                        return scalar(connection, "SELECT max(id) FROM eventstore WHERE subject = ?", subject);
                    }

                    private long latestPlayhead(Connection connection, String subject) throws SQLException {
                        Long playhead =
                                scalar(connection, "SELECT max(playhead) FROM eventstore WHERE subject = ?", subject);
                        return playhead == null ? 0L : playhead;
                    }

                    private Long latestIdOfType(Connection connection, String subject, String type) throws SQLException {
                        try (PreparedStatement statement = connection.prepareStatement(
                                "SELECT max(id) FROM eventstore WHERE subject = ? AND type = ?")) {
                            statement.setString(1, subject);
                            statement.setString(2, type);
                            try (ResultSet rows = statement.executeQuery()) {
                                rows.next();
                                long value = rows.getLong(1);
                                return rows.wasNull() ? null : value;
                            }
                        }
                    }

                    private Long scalar(Connection connection, String sql, String subject) throws SQLException {
                        try (PreparedStatement statement = connection.prepareStatement(sql)) {
                            statement.setString(1, subject);
                            try (ResultSet rows = statement.executeQuery()) {
                                rows.next();
                                long value = rows.getLong(1);
                                return rows.wasNull() ? null : value;
                            }
                        }
                    }

                    private static Long lowerBound(Set<Option> options) {
                        for (Option option : options) {
                            if (option instanceof Option.LowerBoundExclusive bound) {
                                return Long.parseLong(bound.id());
                            }
                            if (option instanceof Option.LowerBoundInclusive bound) {
                                return Long.parseLong(bound.id()) - 1;
                            }
                        }
                        return null;
                    }

                    /** The framework maps a 409 onto {@code ConcurrencyException}; that is the contract for a lost race. */
                    private static ClientException violated(String message) {
                        return new ClientException.HttpException.HttpClientException(message, 409);
                    }
                }
                """
                .replace("{{pkg}}", basePackage);
    }

    static String devController(String basePackage) {
        return """
                package {{pkg}}.dev;

                import java.io.IOException;
                import java.io.UncheckedIOException;
                import java.nio.charset.StandardCharsets;
                import java.util.ArrayList;
                import java.util.LinkedHashMap;
                import java.util.List;
                import java.util.Map;
                import org.springframework.core.io.ClassPathResource;
                import org.springframework.http.MediaType;
                import org.springframework.jdbc.core.JdbcTemplate;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;
                import tools.jackson.databind.ObjectMapper;

                /**
                 * Dev-only window onto the app for an external domain console (0004): the model catalog, the
                 * authoring BPMN and the raw event stream. Not part of the domain API - never expose it in production.
                 */
                @RestController
                public class DevController {

                    private static final int WINDOW = 50;

                    private final JdbcTemplate jdbcTemplate;
                    private final ObjectMapper objectMapper;

                    public DevController(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
                        this.jdbcTemplate = jdbcTemplate;
                        this.objectMapper = objectMapper;
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
                        return jdbcTemplate.query(
                                "SELECT id, subject, type, data, playhead, recorded_on FROM eventstore"
                                        + " ORDER BY id DESC LIMIT " + WINDOW,
                                (rows, index) -> {
                                    String subject = rows.getString("subject");
                                    String[] segments = subject.split("/");

                                    Map<?, ?> data = objectMapper.readValue(rows.getString("data"), Map.class);

                                    Map<String, Object> row = new LinkedHashMap<>();
                                    row.put("id", String.valueOf(rows.getLong("id")));
                                    row.put("aggregate", segments.length > 1 ? segments[1] : "");
                                    row.put("aggregate_id", segments.length > 0 ? segments[segments.length - 1] : "");
                                    // Unlike EventSourcingDB, this store keeps a real per-subject sequence.
                                    row.put("playhead", rows.getLong("playhead"));
                                    row.put("event", rows.getString("type"));
                                    row.put("payload", data.get("payload"));
                                    row.put("recorded_on", rows.getTimestamp("recorded_on").toInstant().toString());
                                    return row;
                                });
                    }

                    private static String classpath(String name) {
                        try {
                            return new ClassPathResource(name).getContentAsString(StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            throw new UncheckedIOException("failed to read " + name, e);
                        }
                    }
                }
                """
                .replace("{{pkg}}", basePackage);
    }

    static String readme(String appName, String domain) {
        return Bootstrap.readme(appName, domain)
                .replace(
                        "[EventSourcingDB](https://www.eventsourcingdb.io/), with MongoDB read models.",
                        "PostgreSQL as the event store, with MongoDB read models.")
                .replace(
                        "generators, so all of them can run against one store.",
                        "generators. The event log lives in the `eventstore` table, which keeps a real per-subject"
                                + " `playhead`.");
    }
}
