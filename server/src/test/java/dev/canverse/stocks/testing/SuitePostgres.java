package dev.canverse.stocks.testing;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Owns the PostgreSQL container for the lifetime of the normal integration-test JVM. */
public final class SuitePostgres {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17")).withDatabaseName("stocks_test")
            .withUsername("stocks_test").withPassword("stocks_test");

    private static boolean started;

    private SuitePostgres() {}

    public static synchronized PostgreSQLContainer<?> start() {
        if (!started) {
            POSTGRES.start();
            Runtime.getRuntime().addShutdownHook(new Thread(POSTGRES::stop, "stocks-test-postgres-shutdown"));
            started = true;
        }
        return POSTGRES;
    }
}
