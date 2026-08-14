package io.github.rsweet.esdm2opencqrs.adapter.opencqrs;

/**
 * The stack-fixed part of an emitted app: build files, container definition and the framework
 * plumbing that does not vary with the model (marshaller, progress tracker, CORS, error mapping).
 */
final class Bootstrap {

    static final String OPENCQRS_VERSION = "2.0.0";
    static final String SPRING_BOOT_VERSION = "4.1.0";
    static final String APP_VERSION = "0.1.0";

    private Bootstrap() {}

    static String pom(String appName) {
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
                    <description>Generated from an ESDM model - Spring Boot + OpenCQRS on EventSourcingDB</description>

                    <properties>
                        <java.version>21</java.version>
                        <opencqrs.version>{{opencqrs}}</opencqrs.version>
                    </properties>

                    <dependencies>
                        <dependency>
                            <groupId>com.opencqrs</groupId>
                            <artifactId>framework-spring-boot-starter</artifactId>
                            <version>${opencqrs.version}</version>
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
                            <groupId>com.opencqrs</groupId>
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
                .replace("{{boot}}", SPRING_BOOT_VERSION)
                .replace("{{opencqrs}}", OPENCQRS_VERSION)
                .replace("{{version}}", APP_VERSION);
    }

    static String dockerfile(String appName) {
        return """
                FROM maven:3.9-eclipse-temurin-21 AS build
                WORKDIR /build
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
                .replace("{{version}}", APP_VERSION);
    }

    static String compose() {
        return """
                # Generated stack: esdb (EventSourcingDB event store + UI), mongo (read models),
                # api (Spring Boot HTTP + in-process OpenCQRS projections).
                services:
                  esdb:
                    image: thenativeweb/eventsourcingdb:1.2.0
                    command:
                      - run
                      - --api-token=secret
                      - --data-directory-temporary
                      - --http-enabled
                      - --https-enabled=false
                      - --with-ui
                    ports:
                      - "3000:3000"

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
                      ESDB_URL: "http://esdb:3000"
                      ESDB_API_TOKEN: secret
                    depends_on:
                      - esdb
                      - mongo
                    ports:
                      - "8080:8080"

                # Domain console: this stack serves the 0004 dev contract (/_dev/*) - point the
                # esdm-vue-reader viewer at http://localhost:8080 for commands / read models / events.

                volumes:
                  mongo-data:
                """;
    }

    static String dockerignore() {
        return """
                target/
                .env
                *.iml
                .idea/
                """;
    }

    static String envExample() {
        return """
                HTTP_PORT=8080

                MONGO_DB=app
                MONGO_URL=mongodb://mongo:27017

                ESDB_URL=http://esdb:3000
                ESDB_API_TOKEN=secret
                """;
    }

    static String applicationProperties(String appName) {
        return """
                spring.application.name={{app}}

                server.port=${HTTP_PORT:8080}

                esdb.server.uri=${ESDB_URL:http://localhost:3000}
                esdb.server.api-token=${ESDB_API_TOKEN:secret}

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

    static String readme(String appName, String domain) {
        return """
                # {{app}}

                Generated from the `{{domain}}` ESDM model by **esdm-2-opencqrs**. Do not hand-edit:
                change the model and regenerate.

                Spring Boot + [OpenCQRS](https://docs.opencqrs.com/) on
                [EventSourcingDB](https://www.eventsourcingdb.io/), with MongoDB read models.

                ## Run

                ```sh
                docker compose up --build
                ```

                The API listens on `http://localhost:8080`.

                ## Surface

                | Route                        | Purpose                                  |
                | ---------------------------- | ---------------------------------------- |
                | `POST /<context>/<command>`  | fire a command                           |
                | `GET  /<context>/<query>`    | read a read model                        |
                | `GET  /_dev/catalog`         | the model catalog (0004 console contract) |
                | `GET  /_dev/bpmn`            | the authoring diagram                    |
                | `GET  /_dev/events`          | the newest 50 raw events                 |

                Events are written as `<domain>.<aggregate>.<event>` on subject `/<aggregate>/<id>`,
                with `data = { payload, nimbusMeta }` - the envelope shared with the sibling
                generators, so all of them can run against one store.

                ## Tests

                ```sh
                mvn test
                ```

                The emitted tests are the model's given-when-then scenarios, run against OpenCQRS'
                `CommandHandlingTestFixture` - no database required.
                """
                .replace("{{app}}", appName)
                .replace("{{domain}}", domain);
    }

    static String application(String basePackage) {
        return """
                package {{pkg}};

                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;

                @SpringBootApplication
                public class Application {

                    public static void main(String[] args) {
                        SpringApplication.run(Application.class, args);
                    }
                }
                """
                .replace("{{pkg}}", basePackage);
    }

    static String marshaller(String basePackage) {
        return """
                package {{pkg}}.config;

                import com.opencqrs.framework.CqrsFrameworkException;
                import com.opencqrs.framework.serialization.EventData;
                import com.opencqrs.framework.serialization.EventDataMarshaller;
                import java.util.Map;
                import tools.jackson.core.JacksonException;
                import tools.jackson.databind.ObjectMapper;

                /**
                 * Writes the ESDM family event envelope: {@code data = { payload, nimbusMeta }}. OpenCQRS' own
                 * marshaller names the meta key {@code metadata}; the sibling generators all write
                 * {@code nimbusMeta}, and store interchange depends on that key matching.
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
                                    deserialized.nimbusMeta() == null ? Map.of() : deserialized.nimbusMeta(),
                                    deserialized.payload());
                        } catch (JacksonException e) {
                            throw new CqrsFrameworkException.NonTransientException("failed to deserialize: " + json, e);
                        }
                    }

                    record NimbusData<E>(Map<String, ?> nimbusMeta, E payload) {}
                }
                """
                .replace("{{pkg}}", basePackage);
    }

    static String progressTracker(String basePackage) {
        return """
                package {{pkg}}.config;

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
                        ProgressDocument document =
                                mongoTemplate.findById(key(group, partition), ProgressDocument.class, COLLECTION);
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
                """
                .replace("{{pkg}}", basePackage);
    }

    static String webConfiguration(String basePackage) {
        return """
                package {{pkg}}.config;

                import org.springframework.context.annotation.Configuration;
                import org.springframework.web.servlet.config.annotation.CorsRegistry;
                import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

                /** Permissive CORS so a domain console served from another origin can drive this app (0004 section 5). */
                @Configuration
                public class WebConfiguration implements WebMvcConfigurer {

                    @Override
                    public void addCorsMappings(CorsRegistry registry) {
                        registry.addMapping("/**")
                                .allowedOriginPatterns("*")
                                .allowedMethods("GET", "POST", "OPTIONS")
                                .allowedHeaders("*");
                    }
                }
                """
                .replace("{{pkg}}", basePackage);
    }

    static String apiError(String basePackage) {
        return """
                package {{pkg}}.support;

                import java.util.LinkedHashMap;
                import java.util.Map;

                /** The family's error body: {@code { error, message, details }}. */
                public record ApiError(String error, String message, Map<String, Object> details) {

                    public static Map<String, Object> details(String... keysAndValues) {
                        Map<String, Object> details = new LinkedHashMap<>();
                        for (int i = 0; i < keysAndValues.length; i += 2) {
                            details.put(keysAndValues[i], keysAndValues[i + 1]);
                        }
                        return details;
                    }
                }
                """
                .replace("{{pkg}}", basePackage);
    }

    static String domainRuleException(String basePackage) {
        return """
                package {{pkg}}.support;

                /**
                 * A command was refused by a domain rule. A model's {@code rejection:} says that a command must be
                 * refused, not which rule refuses it, so emitted scenario tests assert this type rather than a subtype.
                 */
                public abstract class DomainRuleException extends RuntimeException {

                    private final String command;

                    protected DomainRuleException(String command, String message) {
                        super(message);
                        this.command = command;
                    }

                    public String command() {
                        return command;
                    }
                }
                """
                .replace("{{pkg}}", basePackage);
    }

    static String illegalTransition(String basePackage) {
        return """
                package {{pkg}}.support;

                /** Raised when the aggregate state machine (0001) does not admit a command in the current state. */
                public class IllegalTransitionException extends DomainRuleException {

                    public IllegalTransitionException(String command, String state) {
                        super(command, command + " is not allowed while \\"" + state + "\\"");
                    }
                }
                """
                .replace("{{pkg}}", basePackage);
    }

    static String guardViolation(String basePackage) {
        return """
                package {{pkg}}.support;

                /** Raised when a FEEL precondition (0002) on an admitted command evaluates false. */
                public class GuardViolationException extends DomainRuleException {

                    public GuardViolationException(String command, String expression) {
                        super(command, command + " requires: " + expression);
                    }
                }
                """
                .replace("{{pkg}}", basePackage);
    }

    static String guards(String basePackage) {
        return """
                package {{pkg}}.support;

                /** Runtime support for compiled FEEL guards (proposal 0002). */
                public final class Guards {

                    private Guards() {}

                    /**
                     * Orders two guard operands the way FEEL does: numerically when both are numbers, otherwise by
                     * natural order for like types (dates and timestamps are ISO-8601, so lexical order is chronological).
                     */
                    @SuppressWarnings("unchecked")
                    public static int compare(Object left, Object right) {
                        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
                            return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue());
                        }
                        if (left instanceof Comparable<?> && left.getClass().isInstance(right)) {
                            return ((Comparable<Object>) left).compareTo(right);
                        }
                        return String.valueOf(left).compareTo(String.valueOf(right));
                    }

                    /**
                     * FEEL equality. Numbers compare by value, so a {@code Long} field and an {@code int} literal are
                     * equal when they should be - {@link java.util.Objects#equals} would compare box types and say no.
                     */
                    /**
                     * FEEL arithmetic, always in the real number domain: Java is the only target
                     * language that would divide two integers as integers.
                     */
                    public static double add(Object left, Object right) {
                        return number(left) + number(right);
                    }

                    public static double subtract(Object left, Object right) {
                        return number(left) - number(right);
                    }

                    public static double multiply(Object left, Object right) {
                        return number(left) * number(right);
                    }

                    /**
                     * FEEL yields null on a zero divisor, and null in a predicate is false. NaN carries
                     * that here, because {@link #ordered} makes every comparison against it false - the
                     * same outcome the sibling languages reach with their own NaN.
                     */
                    public static double divide(Object left, Object right) {
                        double divisor = number(right);

                        return divisor == 0 ? Double.NaN : number(left) / divisor;
                    }

                    /** An ISO-8601 date is already this family's wire form, so date() only validates it. */
                    public static String date(Object value) {
                        return java.time.LocalDate.parse(String.valueOf(value)).toString();
                    }

                    /** A date plus whole days, back in the ISO form everything else compares. */
                    public static String datePlusDays(Object date, Object days) {
                        return java.time.LocalDate.parse(String.valueOf(date))
                                .plusDays((long) number(days))
                                .toString();
                    }

                    public static boolean startsWith(Object value, Object prefix) {
                        return String.valueOf(value).startsWith(String.valueOf(prefix));
                    }

                    public static boolean endsWith(Object value, Object suffix) {
                        return String.valueOf(value).endsWith(String.valueOf(suffix));
                    }

                    public static boolean contains(Object value, Object part) {
                        return String.valueOf(value).contains(String.valueOf(part));
                    }

                    /** Ordering as a predicate: an unusable operand answers false in both directions. */
                    public static boolean ordered(String operator, Object left, Object right) {
                        if (left instanceof Double leftNaN && leftNaN.isNaN()) {
                            return false;
                        }
                        if (right instanceof Double rightNaN && rightNaN.isNaN()) {
                            return false;
                        }
                        int order = compare(left, right);

                        return switch (operator) {
                            case "<" -> order < 0;
                            case "<=" -> order <= 0;
                            case ">" -> order > 0;
                            default -> order >= 0;
                        };
                    }

                    private static double number(Object value) {
                        return value instanceof Number n ? n.doubleValue() : Double.NaN;
                    }

                    public static boolean equal(Object left, Object right) {
                        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
                            return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue()) == 0;
                        }
                        return java.util.Objects.equals(left, right);
                    }
                }
                """
                .replace("{{pkg}}", basePackage);
    }

    static String exceptionHandler(String basePackage) {
        return """
                package {{pkg}}.config;

                import static {{pkg}}.support.ApiError.details;

                import {{pkg}}.support.ApiError;
                import {{pkg}}.support.GuardViolationException;
                import {{pkg}}.support.IllegalTransitionException;
                import com.opencqrs.framework.client.ConcurrencyException;
                import com.opencqrs.framework.command.CommandSubjectAlreadyExistsException;
                import com.opencqrs.framework.command.CommandSubjectDoesNotExistException;
                import java.util.Map;
                import org.springframework.http.HttpStatus;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.ExceptionHandler;
                import org.springframework.web.bind.annotation.RestControllerAdvice;

                /** Maps domain and framework failures onto the family's HTTP contract: 409 on a rejected rule, 404 on a missing subject. */
                @RestControllerAdvice
                public class ApiExceptionHandler {

                    @ExceptionHandler(IllegalTransitionException.class)
                    public ResponseEntity<ApiError> onIllegalTransition(IllegalTransitionException exception) {
                        return conflict(
                                exception.getMessage(),
                                details("errorCode", "ILLEGAL_TRANSITION", "command", exception.command()));
                    }

                    @ExceptionHandler(GuardViolationException.class)
                    public ResponseEntity<ApiError> onGuardViolation(GuardViolationException exception) {
                        return conflict(
                                exception.getMessage(),
                                details("errorCode", "GUARD_VIOLATION", "command", exception.command()));
                    }

                    @ExceptionHandler(CommandSubjectAlreadyExistsException.class)
                    public ResponseEntity<ApiError> onSubjectAlreadyExists(CommandSubjectAlreadyExistsException exception) {
                        return conflict("subject already exists", details("errorCode", "SUBJECT_EXISTS"));
                    }

                    @ExceptionHandler(ConcurrencyException.class)
                    public ResponseEntity<ApiError> onConcurrency(ConcurrencyException exception) {
                        return conflict("concurrent modification", details("errorCode", "CONCURRENCY_CONFLICT"));
                    }

                    @ExceptionHandler(CommandSubjectDoesNotExistException.class)
                    public ResponseEntity<ApiError> onSubjectDoesNotExist(CommandSubjectDoesNotExistException exception) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(new ApiError("NOT_FOUND", "subject not found", details("errorCode", "SUBJECT_NOT_FOUND")));
                    }

                    private static ResponseEntity<ApiError> conflict(String message, Map<String, Object> details) {
                        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("CONFLICT", message, details));
                    }
                }
                """
                .replace("{{pkg}}", basePackage);
    }

    static String devController(String basePackage) {
        return """
                package {{pkg}}.dev;

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
                """
                .replace("{{pkg}}", basePackage);
    }
}
