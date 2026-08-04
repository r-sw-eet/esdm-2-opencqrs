# esdm-2-opencqrs - scope

Plan of record for the Java codegen of the ESDM family. Written 2026-08-04, before any code existed;
**phases 0-6 were then built and verified the same day**. Where the plan and the built system differ,
the built system won and this document says so inline. See `HANDOFF.md` for current state.

---

## 1. Where it sits

The family is four generators around one spec: `../esdm-extensions` (proposals 0001 state machines,
0002 FEEL, 0003 BPMN mapping, 0004 domain-console contract) plus the C4 cross-generator conformance
data. Each generator is standalone, written in its own stack's language, and emits an app that
behaves identically to the others.

| Repo                  | Language   | Emits                               | Event store(s) |
|-----------------------|------------|-------------------------------------|----------------|
| `esdm-2-symfony`      | PHP        | Symfony + patchlevel/event-sourcing | Postgres, ESDB |
| `esdm-2-nimbus`       | TypeScript | Nimbus + Hono                       | ESDB, Postgres |
| `esdm-2-python`       | Python     | Django                              | Postgres, ESDB |
| **`esdm-2-opencqrs`** | **Java**   | **Spring Boot + OpenCQRS**          | **ESDB only**  |

`esdm-2-nimbus` is the reference for the emission shape. `esdm-2-symfony` is the C4 oracle that
records the golden answers.

> **Byte identity is not a family property.** It only ever held between two generators emitting the
> *same* target: the PHP repo's since-deleted `NimbusEventSourcingDb` adapter and its TypeScript
> successor. What survives there is a nimbus-internal regression lock. Across stacks the binding
> guarantee is **C4 behavioral conformance** plus the shared wire envelope - which is what this repo
> is held to.

**Why OpenCQRS is the right fourth stack:** it is ESDB-native. Its own `esdb-client` module speaks
the same store the family already writes to, its `Command.getSubject()` is the family's
`/<aggregate>/<id>` convention, and its `SubjectCondition` is the family's create/mutate
precondition. The framework was built for exactly the shape ESDM describes.

Upstream facts (re-verified against Maven Central 2026-08-04): **OpenCQRS 2.0.0** (released
2026-06-26; 1.0.0 is what the first draft of this document assumed),
`com.opencqrs:framework-spring-boot-starter` and `com.opencqrs:framework-test`, Apache-2.0, by
Digital Frontiers GmbH & Co. KG. It pins **Spring Boot 4.1.0**, which means Jackson 3
(`tools.jackson`) and JDK 21+. Modules: `esdb-client`, `framework`, the two Spring Boot
autoconfigure/starter pairs for each, and `framework-test`.

---

## 2. Target

| Field       | Value                                                    |
|-------------|----------------------------------------------------------|
| Target id   | `opencqrs-eventsourcingdb`                               |
| Slug        | `opencqrs` (output lands in `<app>/generated/opencqrs/`) |
| Event store | EventSourcingDB, via OpenCQRS `esdb-client`              |
| Read models | MongoDB (`rm_*` collections), Spring Data MongoDB        |
| HTTP        | Spring Web MVC                                           |
| Build       | Maven (emitted app), Gradle (the generator itself)       |

**One target, not two.** Every sibling carries two targets along the event-store axis. OpenCQRS is
bound to EventSourcingDB, so that axis collapses. A second Java target would have to come from a
different runtime (Axon, a hand-rolled JDBC store) and is explicitly out of scope.

*Revised: the axis does not collapse. A second target `opencqrs-postgres` exists as a prototype and
passes C4 with zero divergences; it needs a three-file upstream change to OpenCQRS. See section 10.*

---

## 3. Wire conformance - the decisive finding

The family envelope, which makes the four generators' apps store-interchangeable:

```
type     <domain>.<aggregate>.<kebab-event>     e.g. todo.task.task-completion-changed
subject  /<aggregate>/<id>                      e.g. /task/9f3c...
data     { payload: {...camelCase...}, nimbusMeta: { correlationid } }
```

OpenCQRS's default serialization (`com.opencqrs.framework.serialization.JacksonEventDataMarshaller`)
produces:

```java
return Map.of("payload", payload, "metadata", metaData);
```

So the default envelope is `{ payload, metadata }` - the family's shape, one key name apart. And
`EventDataMarshaller` is a **public, pluggable interface** (`serialize(EventData<E>)` /
`deserialize(Map, Class<E>)`), whose javadoc explicitly frames it as the interoperability seam.

**Therefore:** the emitted app ships a ~30-line `NimbusEventDataMarshaller implements
EventDataMarshaller` that writes `nimbusMeta` instead of `metadata`, and the target is fully
store-interchangeable with the nimbus / Symfony / Django apps. This was the single biggest risk in
the whole plan and it is resolved on paper.

The other two wire knobs are equally controllable:

- **Event type strings** - `PreconfiguredAssignableClassEventTypeResolver` takes an explicit
  `Map<String, Class<?>>`, so the generator emits `todo.task.task-completed -> TaskCompletedEvent`
  for every event. Caveat from the docs: once a custom resolver is defined, **all** event classes
  must be registered, the class-name fallback is gone. The generator emits the complete map, so
  this is a non-issue by construction.
- **Subjects** - `Command.getSubject()` is ours to write: `return "/task/" + id();`.

---

## 4. The ESDM -> OpenCQRS mapping

| ESDM                             | OpenCQRS / Spring emission                                                                                             |
|----------------------------------|------------------------------------------------------------------------------------------------------------------------|
| bounded context                  | Java package + URL segment (`/tasks/...`)                                                                              |
| aggregate                        | subject prefix `/task/{id}` + a write-model `record TaskState(...)`                                                    |
| command + fields                 | `record AddTaskCommand(...) implements Command` with `getSubject()` and `getSubjectCondition()`                        |
| lifecycle `create`               | `SubjectCondition.PRISTINE`                                                                                            |
| lifecycle `mutate` / `delete`    | `SubjectCondition.EXISTS`                                                                                              |
| command handler                  | `@CommandHandling` method on a `@CommandHandlerConfiguration` class, publishing via `CommandEventPublisher<TaskState>` |
| event + fields                   | plain `record TaskAddedEvent(...)`, registered in the type resolver                                                    |
| aggregate fold / `apply`         | one `@StateRebuilding public TaskState on(TaskAddedEvent e, TaskState state)` per event                                |
| 0001 state machine `states`      | a `status` field on the write-model record                                                                             |
| 0001 `admits[].when` + 0002 FEEL | a compiled Java boolean guard at the top of the `@CommandHandling` method, throwing `IllegalTransitionException`       |
| read model + projection          | `@Document("rm_tasks")` row record + Spring Data `MongoRepository` + `@EventHandling("<read-model>")` projector        |
| query                            | `@RestController` `GET /{context}/{query}` over the repository                                                         |
| command HTTP                     | `@RestController` `POST /{context}/{command}` -> `CommandRouter.send(...)`                                             |
| policy (reaction)                | `@EventHandling("<policy>")` component that builds a command and sends it through `CommandRouter`                      |
| GWT feature / scenario           | `@CommandHandlingTest` class using `CommandHandlingTestFixture`                                                        |
| 0004 dev contract                | `@RestController` serving `/_dev/catalog`, `/_dev/bpmn`, `/_dev/events` + permissive CORS                              |

### Rejections -> HTTP status

The family answers 409 on a rejected domain rule. One `@RestControllerAdvice` maps:

| Exception                              | Status | Meaning                                  |
|----------------------------------------|--------|------------------------------------------|
| `CommandSubjectAlreadyExistsException` | 409    | `PRISTINE` violated (create on existing) |
| `CommandSubjectDoesNotExistException`  | 404    | `EXISTS` violated (mutate on missing)    |
| emitted `IllegalTransitionException`   | 409    | 0001 state machine refused the command   |
| emitted `GuardViolationException`      | 409    | 0002 FEEL precondition refused it        |
| `ConcurrencyException`                 | 409    | append precondition lost a race          |

**Correction from the build.** A guarded mutate command must be emitted with
`SubjectCondition.NONE`, not `EXISTS`. `CommandRouter` checks the subject condition *before* the
handler runs, so `EXISTS` would answer an unknown aggregate with 404 - but the C4 golden requires
409 `ILLEGAL_TRANSITION` with `is not allowed while "undefined"`, because the state machine guard is
what refuses it. `EXISTS` is only emitted for mutate/delete commands the model does *not* guard.

Both emitted exceptions extend an emitted `DomainRuleException`. A model's `rejection:` says a
command must be refused, not which rule refuses it, so emitted scenario tests assert the supertype.

### Projection progress

OpenCQRS's `EventHandlingProcessor` tracks per-group progress via a `ProgressTracker` bean. Shipped
implementations are `JdbcProgressTracker` (durable, SQL) and `InMemoryProgressTracker` (replays
everything on restart). Our read side is MongoDB, and adding Postgres purely for a cursor table
would put three datastores in the compose file.

**Decision:** emit an app-local `MongoProgressTracker implements ProgressTracker`, wired through
`opencqrs.event-handling.standard.progress.tracker-ref`. This mirrors what `nimbus-postgres` already
does by emitting its own `src/eventstore/` store code, and keeps the stack at ESDB + Mongo.

---

## 5. The emitted app (for `examples/todo`)

Package root from `esdmgen.yaml` `options.basePackage` (a new option the other targets don't need),
default `app.<domain>`.

```
generated/opencqrs/
  pom.xml                       Spring Boot parent, OpenCQRS starter, spring-boot-starter-data-mongodb,
                                spring-boot-starter-web, framework-test (test scope)
  Dockerfile                    maven:3.9-eclipse-temurin-21 builder -> eclipse-temurin:21-jre
  compose.yaml                  api + eventsourcingdb + mongodb
  .dockerignore  .env.example  README.md
  src/main/resources/
    application.properties      esdb.server.uri / api-token, spring.data.mongodb.uri, opencqrs.*
    catalog.json                the 0004 catalog, emitted from the model
  src/main/java/app/todo/
    Application.java
    config/
      EventTypeConfiguration.java     PreconfiguredAssignableClassEventTypeResolver, all events
      NimbusEventDataMarshaller.java  data = { payload, nimbusMeta }
      MongoProgressTracker.java       durable @EventHandling progress in Mongo
      WebConfiguration.java           permissive CORS for the domain console
      ApiExceptionHandler.java        the status mapping above
    write/tasks/task/
      commands/AddTaskCommand.java  SetCompletionCommand.java  ...
      events/TaskAddedEvent.java  TaskCompletionChangedEvent.java  ...
      TaskState.java                  the write model record
      TaskHandlers.java               @CommandHandlerConfiguration: @CommandHandling + @StateRebuilding
      TaskController.java             POST /tasks/add-task, POST /tasks/set-completion, ...
    read/tasks/tasks/
      TasksRow.java                   @Document("rm_tasks")
      TasksRepository.java            MongoRepository
      TasksProjector.java             @EventHandling("tasks")
      TasksQueryController.java       GET /tasks/find-tasks
    policies/
      NotifyOnTaskAddedPolicy.java    @EventHandling -> CommandRouter.send
    dev/DevController.java            /_dev/catalog | /_dev/bpmn | /_dev/events
  src/test/java/app/todo/
    TaskLifecycleTest.java            @CommandHandlingTest, one @Test per GWT scenario
```

Emitted test shape, straight off the model's `feature` documents:

```java
@CommandHandlingTest
class TaskLifecycleTest {
    @Test
    void setCompletionOnOpenTask(@Autowired CommandHandlingTestFixture<SetCompletionCommand> fixture) {
        fixture.given()
               .event(new TaskAddedEvent("t-1", "buy milk"))
               .when(new SetCompletionCommand("t-1", true))
               .succeeds()
               .allEvents()
               .exactly(new TaskCompletionChangedEvent("t-1", true));
    }
}
```

This is the one place where OpenCQRS beats every sibling: the family's given/when/then documents map
onto a **first-party** fixture API instead of a hand-rolled harness.

---

## 6. The generator

Gradle (Kotlin DSL) + Java 21, `application` plugin, picocli CLI. Root package
`io.github.rsweet.esdm2opencqrs`.

```
esdm-2-opencqrs/
  settings.gradle.kts  build.gradle.kts  gradlew  gradle/wrapper/
  src/main/java/io/github/rsweet/esdm2opencqrs/
    Main.java                      picocli root
    cli/GenerateCommand.java  cli/TargetsCommand.java  cli/ConformanceCommand.java
    model/DocumentLoader.java  ModelFactory.java  Yamls.java  + model records
    feel/Lexer.java  Parser.java  FeelNode.java  Feel.java
    lint/EsdmLinter.java           shells out to the esdm binary
    conformance/ConformanceRunner.java  Observations.java  Differ.java
    adapter/Adapter.java  GeneratedProject.java  AdapterRegistry.java
    adapter/opencqrs/OpenCqrsAdapter.java  Bootstrap.java  FeelJava.java  FeelHints.java
    adapter/opencqrs/Naming.java  JavaTypes.java  Json.java  Q.java
    support/Str.java
  src/test/java/...                JUnit 5 + AssertJ
  examples/{todo,orders,commerce,factory,manufacturing}/
  reference/todo/                  hand-written reference app (phase 0)
  scripts/examples.sh  conformance.sh  fetch-esdm.sh
  tools/esdm                       gitignored, fetched
```

CLI, matching the family:

```sh
./gradlew run --args="generate examples/todo [--target opencqrs-eventsourcingdb] [--skip-lint] [--strict]"
./gradlew run --args="targets"
./gradlew test                  # the generator's own unit tests
scripts/examples.sh             # smoke: generate every example, compile it, run its emitted tests
scripts/conformance.sh todo     # C4 against ../esdm-extensions/conformance
scripts/fetch-esdm.sh           # pull the pinned esdm binary into tools/ for the lint gate
```

### Port budget

Subsystems ported from `esdm-2-nimbus` (its line counts; Java runs roughly 1.3-1.6x):

| Subsystem                          | TS lines | Java, as built | Notes                                    |
|------------------------------------|----------|----------------|------------------------------------------|
| `model/` loader + factory + types  | 733      | 854            | plus `Yamls` for the YAML 1.1 `on:` trap |
| `feel/` lexer + parser + validator | 261      | 311            | mechanical                               |
| `lint/EsdmLinter`                  | 170      | 177            | process spawn + JSON parse               |
| `support/Str`                      | 22       | 33             | naming helpers; they define wire strings |
| `cli/` + `Main`                    | 404      | 333            | picocli absorbed much of it              |
| `adapter/` seam + **the emitter**  | 72 + new | 2298           | the only genuinely new code              |
| `conformance/` C4 runner           | n/a      | 675            | not in the original budget               |
| `bpmn/` parser + mapper            | 969      | -              | phase 7, optional, not started           |

4705 lines of generator plus 413 of unit test. The estimate held: the ported subsystems came in
close to the TS originals, and the emitter landed at the low end of the 2400-3200 guess.

---

## 7. Spikes - all answered in phase 0

Each was a question the docs left open. All seven are resolved: 1-3 and 5-7 against the published
sources and a live stack, 4 by reading the client's option set.

1. **Marshaller override.** *Answered:* `JacksonEventDataMarshallerAutoConfiguration` is
   `@ConditionalOnMissingBean(EventDataMarshaller.class)`, so a plain `@Bean` wins. No `@Primary`
   needed. Verified live: the store holds `{ payload, nimbusMeta }`.
2. **`ProgressTracker` SPI shape.** *Answered:* two methods, `current(group, partition)` and
   `proceed(group, partition, Supplier<Progress>)`, over a sealed `Progress` of `None`/`Success(id)`.
   `progress.tracker-ref` is resolved as a **bean name** (`getBean(ref, ProgressTracker.class)`), as
   a standard default and per group. The emitted Mongo tracker is ~30 lines.
3. **Fixture rejection API.** *Answered:* `.fails().throwing(Class)`, plus `.throwing(Throwable)`,
   `.throwsSatisfying(...)`, `.violatingExactly(SubjectCondition)`. Emitted tests use
   `.throwing(DomainRuleException.class)`.
4. **`/_dev/events` newest-first slice.** *Answered:* `Option.Order(ANTICHRONOLOGICAL)` exists
   alongside upper/lower bounds. The emitter still reads chronologically into a 50-entry ring buffer
   and reverses, matching nimbus exactly and keeping the row window identical across stacks.
5. **Version floor.** *Answered, and the plan was out of date:* OpenCQRS **2.0.0** pins Spring Boot
   **4.1.0** and Jackson 3 (`tools.jackson`). One trap found live: Spring Boot 4 moved MongoDB
   configuration to `spring.mongodb.*` and deprecated `spring.data.mongodb.*` at **error** level, so
   the old keys are accepted and silently ignored - the app connects to `localhost:27017` and the
   read side times out. The emitter writes the new keys.
6. **Family envelope detail.** *Answered:* `nimbusMeta` carries `correlationid` only. Confirmed in
   the Symfony and Django emitters and on the wire from this app.
7. **Catch-up on boot.** *Answered:* an empty tracker yields `Progress.None`, the processor reads the
   stream from the start, and projections converge on a fresh Mongo. Verified by booting against a
   populated ESDB with an empty read side.

### Also found while building

- **`on:` is a YAML 1.1 boolean.** The 0001 state machine writes transitions as
  `- { on: task-added, to: open }`. SnakeYAML resolves the bare key `on` to `true`, which silently
  dropped every transition and left aggregates in their initial state. The loader now narrows the
  boolean resolver to `true`/`false` (YAML 1.2 core-schema semantics, which is what the sibling
  parsers use). Regression test: `ModelFactoryTest#readsStateMachineTransitionsDespiteTheYamlOnKeyword`.
- **FEEL needs a runtime comparison helper in Java.** `validUntil >= today()` compares ISO date
  strings and `defects = 0` compares a boxed `Long` with an `int` literal. Java has no operator that
  spans either case, so guards compile to `Guards.compare(...) >= 0` and `Guards.equal(...)`.

---

## 8. Phases

Deliverable-based, each one independently checkable. **Phases 0-6 are built and their gates are
met**; phase 7 (BPMN) remains optional and unstarted.

**Phase 0 - reference app.** Hand-write, under `reference/todo/`, exactly the app the `todo` example
should generate. Boot it against ESDB + Mongo, fire the commands, watch the projections converge,
and answer every spike in section 7. This becomes the golden template the emitter reproduces, and it
de-risks the whole project before a line of generator code is written. **Do not skip this.**

**Phase 1 - generator skeleton.** Gradle project, picocli CLI (`generate`, `targets`), the model
layer port, the lint gate, `esdmgen.yaml` parsing, the `Adapter`/`GeneratedProject`/registry seam,
`examples/` copied from the family. Output at this point: an empty file tree with the right name.

**Phase 2 - emitter, write side.** Commands, events, write-model records, `@StateRebuilding`,
`@CommandHandling`, the type resolver config, the marshaller, controllers, exception advice, plus the
app bootstrap (`pom.xml`, Dockerfile, compose, properties). Gate: `examples/todo` generates, compiles
and boots; a POST appends an event with the right type, subject and envelope.

**Phase 3 - emitter, read side.** Mongo row records, repositories, `@EventHandling` projectors, the
Mongo progress tracker, query controllers, policies. Gate: read models converge, queries answer.

**Phase 4 - 0001 + 0002.** FEEL port, guard compilation to Java, state-machine status fields,
`IllegalTransitionException` -> 409. Gate: the FEEL unit tests port green and illegal transitions are
rejected.

**Phase 5 - emitted tests.** GWT features -> `@CommandHandlingTest` classes. Gate: the emitted test
suite runs green inside the generated app (`scripts/emitted-tests.sh`, as in the Symfony repo).

**Phase 6 - 0004 + C4 conformance.** Dev controller, catalog emission, CORS, then
`scripts/conformance.sh` implementing the runner contract from
`../esdm-extensions/conformance/README.md` on host ports 1814x. Gate: `todo` and the other scenarios
match the golden observations with no unregistered divergences. **This phase needs a cross-repo
change** in `esdm-extensions`: add the target slug to each scenario's `targets:` list and list the
new runner in the conformance README. Per workspace rule, those edits stay uncommitted for review.

*Built. The slug registered in `targets:` is `opencqrs` (the adapter's `slug()`, matching how the
other repos list `nimbus`/`python`, not the full target id). `todo`, `orders` and `manufacturing`
pass; `drones` was deliberately left unregistered (nebula's model, never run here) and
`commerce`/`factory` have no golden files. The runner lives in `conformance/` rather than a shell
script - `scripts/conformance.sh` just forwards to it, so the harness is Java like the rest.*

**Phase 7 - optional.** `bpmn:map` port (proposal 0003), so BPMN authoring works here too. Purely
additive; the generator is complete without it. **Not started.**

*What it actually blocks: `../esdm-studio`, the operator console. Its pipeline is draw → map →
generate → run, and the map step shells out to `<codegen> bpmn:map` (`server.ts`, the `CODEGENS`
table). So Studio can drive nimbus and symfony and not this generator, nor python, for want of that
one command.*

*Porting it here would be the wrong fix. The mapper reads BPMN and writes ESDM; its output is
stack-agnostic, which is why nimbus's and symfony's are twins - identical function names, both
mapper files exactly 588 lines. A third copy adds an implementation that can disagree with the other
two about what a diagram means, and nothing would catch it: C4 compares emitted-app behaviour, not
model production. The fix is one mapper the whole family shares, fetched as a pinned binary the way
`scripts/fetch-esdm.sh` already fetches `esdm`. Then Studio calls it once, its `CODEGEN` switch
selects only the generate step, and this generator becomes drivable without a line of BPMN code.*

---

## 9. Non-goals

- ~~A second, non-ESDB target. OpenCQRS is ESDB-bound.~~ **Withdrawn - see section 10.** The claim
  was right about the published API and wrong about the reason: OpenCQRS is not architecturally
  bound to EventSourcingDB, it just does not expose the seam.
- Kotlin output. Java records only, even though OpenCQRS supports both.
- JPA read models. The tutorial uses JPA/H2; the family standard is Mongo `rm_*`, and the 0004
  console depends on it.
- A manual-code seam in the emitted app. Same rule as the siblings: change the model, regenerate.
- Publishing the generator to Maven Central.

---

## 10. The PostgreSQL target

Every sibling repo carries two targets along the event-store axis. Section 2 called that axis
collapsed here, because OpenCQRS "is bound to EventSourcingDB". Measuring the coupling instead of
assuming it showed something better: the framework touches the store through exactly **three
methods**.

| Call                                  | Used by                        |
|---------------------------------------|--------------------------------|
| `read(subject, options, consumer)`    | `EventReader`, `CommandRouter` |
| `observe(subject, options, consumer)` | `EventHandlingProcessor`       |
| `write(candidates, preconditions)`    | `EventRepository`              |

`ping`, `health`, `readSubjects` and EventQL are used by the health indicator and reporting, never
by command handling or event processing. Of the 20 `EsdbClient` references in `framework/`, all but
two are javadoc; the structural ones are `EventReader.ClientRequestor`'s parameter type and
`EventRepository`'s field.

The one blocker is that `EsdbClient` is `final` and named concretely in those two places, so there is
nothing to substitute.

### The upstream change

Extract `EventStoreClient` over those three methods, have `EsdbClient` implement it, and retype
`ClientRequestor` and `EventRepository`. Three files plus the new interface. It is source-compatible
for existing users, because `ClientRequestor` is always written as a lambda and lambdas infer their
parameter type.

Prototyped locally against tag `2.0.0` and published as `2.0.0-esdm-proto`. With it, a second target
is a subclass of the adapter overriding a small store seam - build file, compose, properties, the
`/_dev/events` window and an app-local `EventStoreClient`. Everything the model drives (commands,
events, state rebuilding, projections, policies, catalog, emitted tests) is unchanged.

### The Postgres store

One `eventstore` table. `id` orders the global stream; `playhead` is the per-subject sequence and
`UNIQUE (subject, playhead)` is what makes a concurrent append to one subject fail rather than
interleave. The three `Precondition` variants become SQL checks inside the append transaction, and a
lost race surfaces as a 409 `HttpClientException`, which is what the framework already maps to
`ConcurrencyException`. `observe` streams matching rows then polls for later ids until interrupted -
the blocking contract `EventHandlingProcessor` drives.

The unique constraint is the real serialization point, which is what makes the read-then-write
precondition checks safe: two concurrent appends to one subject both compute the same next playhead,
so one of them loses on insert regardless of what the checks saw.

Three known differences from EventSourcingDB, none of which C4 sees:

- **Latency.** `observe` polls (250 ms) where ESDB pushes, so a projection lands a beat later.
- **Key order.** `data` is `JSONB`, which normalizes key order. Payload *content* is identical, but
  the stored document is not byte-for-byte what was written. `JSON` would preserve it if that ever
  matters; nothing in the family compares stored bytes.
- **No hash chain.** `hash` and `predecessorHash` are null; ESDB maintains a verifiable chain.

One wart worth fixing if this goes upstream: signalling a precondition violation as an *HTTP* 409
from a JDBC client is odd. A dedicated `ClientException.PreconditionViolatedException` would read
better and cost nothing.

### Why it is worth doing

A Postgres-backed store has a real per-subject playhead, so this target matches the golden C4
observations **exactly, with no registered divergences** - unlike every EventSourcingDB-backed
target in the family, which must mask `playhead`. That makes it the strongest possible evidence for
the upstream proposal: an interface, a second implementation, and a cross-generator conformance
suite showing both behave identically.

**Status:** prototype. It depends on a locally published OpenCQRS build, so `opencqrs-postgres` is
not usable from a clean clone until the upstream change lands (or is carried as a fork).
