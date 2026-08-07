package dev.canverse.stocks;

import static org.assertj.core.api.Assertions.assertThat;
import dev.canverse.stocks.platform.id.IdGenerator;
import java.time.Clock;
import java.time.ZoneOffset;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class ContextSmokeTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    Clock clock;

    @Autowired
    IdGenerator idGenerator;

    @Test
    void contextStartsOnJava25AgainstFreshDatabase() {
        assertThat(Runtime.version().feature()).isEqualTo(25);
    }

    @Test
    void zeroReplacementApplicationMigrationsApplied() {
        assertThat(flyway.info().applied()).isEmpty();
    }

    @Test
    void legacyApplicationTablesAreNotPresent() {
        var count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables"
                        + " WHERE table_schema = 'public'"
                        + " AND table_name IN ('portfolio', 'account', 'instrument', 'transaction')",
                Integer.class);
        assertThat(count).isZero();
    }

@Test
void infrastructureBeansAreAvailable() {
    assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    assertThat(idGenerator.next()).isNotNull();
}
}
