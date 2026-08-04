# esdm-2-opencqrs

The **Java codegen** of the ESDM toolchain: it consumes an [ESDM](https://www.esdm.io/) model
(Event-Sourced Domain Modeling - YAML documents describing an event-sourced domain) and emits a
**real, runnable Spring Boot application** built onem, 
[OpenCQRS](https://github.com/open-cqrs/opencqrs), with
[EventSourcingDB](https://www.eventsourcingdb.io/) as the event store and
[MongoDB](https://www.mongodb.com/) for read models.

> Draw the business process. The ESDM toolchain makes it run.

It is the sibling of `esdm-2-nimbus` (TypeScript -> Nimbus), `esdm-2-symfony`
(PHP -> Symfony + patchlevel/event-sourcing) and `esdm-2-python` (Python -> Django): **same model
in, an equivalent event-sourced app out**, in a different stack. All four serve the same HTTP
surface and write the same event envelope, so their apps are behaviorally conformant and can share
a store.

## Status

**Working.** The generator emits, compiles and boots real apps: all five example models generate,
their emitted given-when-then tests run green, and the `todo` scenario passes **C4 cross-generator
conformance** against the golden answers recorded by the Symfony oracle - with no unregistered
divergences. See [SCOPE.md](SCOPE.md) for the design, the ESDM -> OpenCQRS mapping and the
answered spikes.

Authoring from BPMN (proposal 0003) is [bpmn-2-esdm](https://github.com/r-sw-eet/bpmn-2-esdm), a
separate tool: draw the process, and it writes the ESDM this generator reads. The mapping is
stack-agnostic, so one tool serves the whole family and no generator carries its own copy. The
emitted app serves the diagram back at `/_dev/bpmn` for the domain console.

## Target

| Target                     | Slug                | Event store                     | Read models    | Status                          |
|----------------------------|---------------------|---------------------------------|----------------|---------------------------------|
| `opencqrs-eventsourcingdb` | `opencqrs`          | EventSourcingDB (OpenCQRS 2.0)  | MongoDB `rm_*` | conformant                      |
| `opencqrs-postgres`        | `opencqrs-postgres` | PostgreSQL (`eventstore` table) | MongoDB `rm_*` | prototype, needs an upstream PR |

Both targets emit the same app; only the event store differs. The framework reaches its store through
three methods (`read`, `observe`, `write`), so a PostgreSQL store is a plug-in - once OpenCQRS exposes
that seam. Today it names the concrete `EsdbClient`, so the Postgres target builds against a locally
patched OpenCQRS. [SCOPE.md](SCOPE.md) section 10 has the analysis and the proposed three-file change.

Because a Postgres store keeps a real per-subject `playhead`, that target matches the C4 golden
observations **exactly, with zero registered divergences** - EventSourcingDB-backed targets across
the whole family have to mask that field.

## Stack

The generator runs on Java; the **emitted** apps run on this stack:

| Layer                | Technology                                                                                              |
|----------------------|---------------------------------------------------------------------------------------------------------|
| Model / input format | [ESDM](https://www.esdm.io/) + [esdm-extensions](https://github.com/r-sw-eet/esdm-extensions) 0001-0004 |
| Generator runtime    | Java 21 - Gradle - SnakeYAML - picocli                                                                  |
| Generated app        | [Spring Boot](https://spring.io/projects/spring-boot) - [OpenCQRS](https://docs.opencqrs.com/) - Maven  |
| Event store          | [EventSourcingDB](https://www.eventsourcingdb.io/)                                                      |
| Read side            | [MongoDB](https://www.mongodb.com/) projections + query API                                             |

## Getting started

Requires JDK 21 and Docker.

```sh
./gradlew run --args="targets"                          # what this repo can emit
./gradlew run --args="generate examples/todo"           # ESDM model in, app out
cd examples/todo/generated/opencqrs && docker compose up --build
```

The app serves the domain routes on `http://localhost:8080` plus the 0004 console contract at
`/_dev/catalog`, `/_dev/bpmn` and `/_dev/events` - point [esdm-vue-reader](https://github.com/r-sw-eet/esdm-vue-reader)
at it to drive the domain from a browser.

`generate` runs `esdm lint` first and refuses to emit from an invalid model; `scripts/fetch-esdm.sh`
pulls the pinned binary into `tools/`, or pass `--skip-lint`.

## Checks

```sh
./gradlew test                    # the generator's own unit tests
scripts/examples.sh               # every example: generate, compile, run its emitted tests
scripts/conformance.sh todo       # C4 against ../esdm-extensions/conformance
```

## License

[MIT](LICENSE) © 2026 Ralf Süss
