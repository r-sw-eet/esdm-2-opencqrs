package io.github.rsweet.esdm2opencqrs.adapter.opencqrs;

import io.github.rsweet.esdm2opencqrs.adapter.GeneratedProject;

/**
 * The same emission as {@link OpenCqrsAdapter}, on PostgreSQL instead of EventSourcingDB. Only the
 * event store changes: the app supplies its own {@code EventStoreClient} over an {@code eventstore}
 * table, and command handling, projections, policies, the HTTP surface and the emitted tests are
 * byte-identical to the EventSourcingDB target.
 *
 * <p><strong>Requires an OpenCQRS build that exposes the client interface.</strong> Upstream 2.0.0
 * names the concrete {@code EsdbClient} in {@code EventRepository} and {@code
 * EventReader.ClientRequestor}, so there is no seam to plug into. See {@code SCOPE.md} section 10.
 */
public final class OpenCqrsPostgresAdapter extends OpenCqrsAdapter {

    // ---- where the patched OpenCQRS comes from ------------------------------
    // The one place to edit when the patched build moves to a repository, or when the change lands
    // upstream and this target can go back to plain com.opencqrs coordinates.

    /** Group id of the build carrying the {@code EventStoreClient} seam. */
    static final String OPENCQRS_GROUP = "com.opencqrs";

    /** Version of that build. Currently a local {@code publishToMavenLocal}. */
    static final String OPENCQRS_VERSION = "2.0.0-esdm-proto";

    /** Repository serving it, or empty when Maven Central (or the local repository) is enough. */
    static final String OPENCQRS_REPOSITORY = "";

    @Override
    public String name() {
        return "opencqrs-postgres";
    }

    @Override
    public String description() {
        return "Spring Boot + OpenCQRS + PostgreSQL event store + MongoDB read models (needs the EventStoreClient seam).";
    }

    @Override
    public String slug() {
        return "opencqrs-postgres";
    }

    @Override
    protected String pom(String appName) {
        return PostgresBootstrap.pom(appName, OPENCQRS_GROUP, OPENCQRS_VERSION, OPENCQRS_REPOSITORY);
    }

    @Override
    protected String dockerfile(String appName) {
        return PostgresBootstrap.dockerfile(appName);
    }

    @Override
    protected String compose() {
        return PostgresBootstrap.compose();
    }

    @Override
    protected String envExample() {
        return PostgresBootstrap.envExample();
    }

    @Override
    protected String readme(String appName, String domain) {
        return PostgresBootstrap.readme(appName, domain);
    }

    @Override
    protected String applicationProperties(String appName) {
        return PostgresBootstrap.applicationProperties(appName);
    }

    @Override
    protected String devController(String basePackage) {
        return PostgresBootstrap.devController(basePackage);
    }

    @Override
    protected void emitStore(GeneratedProject project, String basePackage) {
        project.add(
                Naming.sourcePath(basePackage + ".store", "PostgresEventStoreClient"),
                PostgresBootstrap.storeClient(basePackage));
        project.add(
                Naming.sourcePath(basePackage + ".config", "EventStoreConfiguration"),
                PostgresBootstrap.eventStoreConfiguration(basePackage));
        project.add("src/main/resources/schema.sql", PostgresBootstrap.schema());
        // Populated with the locally published OpenCQRS build; see the Dockerfile.
        project.add(".m2-overlay/.keep", "");
    }
}
