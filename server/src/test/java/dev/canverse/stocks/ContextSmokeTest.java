package dev.canverse.stocks;

import static org.assertj.core.api.Assertions.assertThat;

import dev.canverse.stocks.platform.id.IdGenerator;
import dev.canverse.stocks.testing.IntegrationTest;
import java.time.Clock;
import java.time.ZoneOffset;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
@IntegrationTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ContextSmokeTest {
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
    void foundationReferenceLedgerAndInvestingMigrationsApplied() {
        var applied = flyway.info().applied();
        assertThat(applied).hasSize(8);
        assertThat(applied[0].getVersion().toString()).isEqualTo("1");
        assertThat(applied[1].getVersion().toString()).isEqualTo("2");
        assertThat(applied[2].getVersion().toString()).isEqualTo("3");
        assertThat(applied[3].getVersion().toString()).isEqualTo("4");
        assertThat(applied[4].getVersion().toString()).isEqualTo("5");
        assertThat(applied[5].getVersion().toString()).isEqualTo("6");
        assertThat(applied[6].getVersion().toString()).isEqualTo("7");
        assertThat(applied[7].getVersion().toString()).isEqualTo("8");
    }

    @Test
    void legacyApplicationTablesAreNotPresent() {
        var count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.tables" + " WHERE table_schema = 'public'" +
                " AND table_name IN ('portfolio', 'account', 'instrument', 'transaction')", Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void fiveFoundationTablesExistInCorrectSchemas() {
        var tables = jdbcTemplate.queryForList("SELECT table_schema || '.' || table_name" + " FROM information_schema.tables" +
                " WHERE (table_schema = 'identity' AND table_name IN ('user_account','auth_identity','device_session'))" +
                " OR (table_schema = 'platform' AND table_name IN ('security_event','job'))", String.class);
        assertThat(tables).containsExactlyInAnyOrder("identity.user_account", "identity.auth_identity", "identity.device_session", "platform.security_event",
                "platform.job");
    }

    @Test
    void manualTradingCreatesItsSecurityAndPositionLedgerTables() {
        var tables = jdbcTemplate.queryForList("SELECT table_schema || '.' || table_name FROM information_schema.tables" +
                " WHERE table_schema = 'ledger' AND table_name IN ('security_posting', 'position_projection')", String.class);
        assertThat(tables).containsExactlyInAnyOrder("ledger.security_posting", "ledger.position_projection");
    }

    @Test
    void noApplicationDomainTableCreatedInPublicSchema() {
        var count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables" + " WHERE table_schema = 'public'" + " AND table_name NOT IN ('flyway_schema_history')",
                Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void infrastructureBeansAreAvailable() {
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
        assertThat(idGenerator.next()).isNotNull();
    }
}
