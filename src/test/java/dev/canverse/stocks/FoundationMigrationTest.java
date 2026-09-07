package dev.canverse.stocks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * PostgreSQL/Testcontainers tests proving V1 migration constraints and cascade behavior.
 *
 * <p>
 * Each test runs in a rolled-back outer transaction. Constraint-violation sub-operations run in an isolated REQUIRES_NEW transaction so that a failed
 * constraint check does not poison the outer connection, which is the normal PostgreSQL behavior inside an aborted transaction block.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Transactional
class FoundationMigrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    // -----------------------------------------------------------------------
    // Schema and table existence
    // -----------------------------------------------------------------------

    @Test
    void allEightApplicationSchemasExist() {
        var schemas = jdbcTemplate.queryForList("SELECT schema_name FROM information_schema.schemata" + " WHERE schema_name IN" +
                " ('identity','reference','ledger','data','money','analysis','asset','platform')" + " ORDER BY schema_name", String.class);
        assertThat(schemas).containsExactlyInAnyOrder("analysis", "asset", "data", "identity", "ledger", "money", "platform", "reference");
    }

    @Test
    void fiveFoundationTablesExistInCorrectSchemas() {
        var tables = jdbcTemplate.queryForList("SELECT table_schema || '.' || table_name" + " FROM information_schema.tables" +
                " WHERE (table_schema = 'identity' AND table_name IN ('user_account','auth_identity','device_session'))" +
                " OR (table_schema = 'platform' AND table_name IN ('security_event','job'))", String.class);
        assertThat(tables).containsExactlyInAnyOrder("identity.user_account", "identity.auth_identity", "identity.device_session", "platform.security_event",
                "platform.job");
    }

    // -----------------------------------------------------------------------
    // identity.user_account constraints
    // -----------------------------------------------------------------------

    @Test
    void duplicateEmailNormalizedRejected() {
        assertConstraintViolation(() -> {
            jdbcTemplate.update("INSERT INTO identity.user_account (id, email, email_normalized) VALUES (?, ?, ?)", id(), "alice@example.com",
                    "alice@example.com");
            jdbcTemplate.update("INSERT INTO identity.user_account (id, email, email_normalized) VALUES (?, ?, ?)", id(), "ALICE@example.com",
                    "alice@example.com"); // same normalized value
        });
    }

    @Test
    void emailWithLeadingWhitespaceRejected() {
        assertConstraintViolation(() -> jdbcTemplate.update("INSERT INTO identity.user_account (id, email, email_normalized) VALUES (?, ?, ?)", id(),
                " alice@example.com", "alice@example.com"));
    }

    @Test
    void emailNormalizedWithUppercaseRejected() {
        assertConstraintViolation(() -> jdbcTemplate.update("INSERT INTO identity.user_account (id, email, email_normalized) VALUES (?, ?, ?)", id(),
                "Alice@example.com", "Alice@example.com")); // uppercase disallowed in normalized
    }

    // -----------------------------------------------------------------------
    // identity.auth_identity constraints
    // -----------------------------------------------------------------------

    @Test
    void authIdentityRejectsUnknownUser() {
        assertConstraintViolation(
                () -> jdbcTemplate.update("INSERT INTO identity.auth_identity (id, user_account_id, provider, provider_subject)" + " VALUES (?, ?, ?, ?)", id(),
                        id(), "LOCAL", "ghost@example.com")); // non-existent user_account_id
    }

    @Test
    void duplicateProviderSubjectRejected() {
        assertConstraintViolation(() -> {
            var userId = id();
            jdbcTemplate.update("INSERT INTO identity.user_account (id, email, email_normalized) VALUES (?, ?, ?)", userId, "bob@example.com",
                    "bob@example.com");
            jdbcTemplate.update("INSERT INTO identity.auth_identity (id, user_account_id, provider, provider_subject)" + " VALUES (?, ?, ?, ?)", id(), userId,
                    "LOCAL", "bob@example.com");
            jdbcTemplate.update("INSERT INTO identity.auth_identity (id, user_account_id, provider, provider_subject)" + " VALUES (?, ?, ?, ?)", id(), userId,
                    "LOCAL", "bob@example.com"); // duplicate (provider, provider_subject)
        });
    }

    // -----------------------------------------------------------------------
    // identity.device_session constraints
    // -----------------------------------------------------------------------

    @Test
    void duplicateRefreshTokenHashRejected() {
        assertConstraintViolation(() -> {
            var userId = id();
            var tokenHash = "h:" + UUID.randomUUID();
            var future = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
            jdbcTemplate.update("INSERT INTO identity.user_account (id, email, email_normalized) VALUES (?, ?, ?)", userId, "carol@example.com",
                    "carol@example.com");
            jdbcTemplate.update(
                    "INSERT INTO identity.device_session" + " (id, user_account_id, family_id, refresh_token_hash, expires_at)" + " VALUES (?, ?, ?, ?, ?)",
                    id(), userId, id(), tokenHash, future);
            jdbcTemplate.update(
                    "INSERT INTO identity.device_session" + " (id, user_account_id, family_id, refresh_token_hash, expires_at)" + " VALUES (?, ?, ?, ?, ?)",
                    id(), userId, id(), tokenHash, future); // same token hash
        });
    }

    @Test
    void sessionExpiresAtOrBeforeCreatedAtRejected() {
        assertConstraintViolation(() -> {
            var userId = id();
            var now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
            jdbcTemplate.update("INSERT INTO identity.user_account (id, email, email_normalized) VALUES (?, ?, ?)", userId, "dave@example.com",
                    "dave@example.com");
            jdbcTemplate.update("INSERT INTO identity.device_session" + " (id, user_account_id, family_id, refresh_token_hash, created_at, expires_at)" +
                    " VALUES (?, ?, ?, ?, ?, ?)", id(), userId, id(), "h:" + UUID.randomUUID(), now, now); // expires_at == created_at
        });
    }

    @Test
    void twoNonRevokedSessionsForSameFamilyRejected() {
        assertConstraintViolation(() -> {
            var userId = id();
            var familyId = id();
            var future = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
            jdbcTemplate.update("INSERT INTO identity.user_account (id, email, email_normalized) VALUES (?, ?, ?)", userId, "eve@example.com",
                    "eve@example.com");
            jdbcTemplate.update(
                    "INSERT INTO identity.device_session" + " (id, user_account_id, family_id, refresh_token_hash, expires_at)" + " VALUES (?, ?, ?, ?, ?)",
                    id(), userId, familyId, "h:A:" + UUID.randomUUID(), future);
            jdbcTemplate.update(
                    "INSERT INTO identity.device_session" + " (id, user_account_id, family_id, refresh_token_hash, expires_at)" + " VALUES (?, ?, ?, ?, ?)",
                    id(), userId, familyId, "h:B:" + UUID.randomUUID(), future); // second non-revoked in same family
        });
    }

    // -----------------------------------------------------------------------
    // platform.job constraints
    // -----------------------------------------------------------------------

    @Test
    void invalidJobStatusRejected() {
        assertConstraintViolation(() -> jdbcTemplate.update("INSERT INTO platform.job (id, job_type, status) VALUES (?, ?, ?)", id(), "IMPORT", "UNKNOWN"));
    }

    @Test
    void runningJobWithoutClaimMetadataRejected() {
        assertConstraintViolation(() -> jdbcTemplate.update("INSERT INTO platform.job (id, job_type, status) VALUES (?, ?, ?)", id(), "IMPORT", "RUNNING")); // claimed_by
                                                                                                                                                             // /
                                                                                                                                                             // claim_token
                                                                                                                                                             // /
                                                                                                                                                             // claimed_at
                                                                                                                                                             // all
                                                                                                                                                             // null
    }

    @Test
    void negativeAttemptCountRejected() {
        assertConstraintViolation(() -> jdbcTemplate.update("INSERT INTO platform.job (id, job_type, attempt_count) VALUES (?, ?, ?)", id(), "IMPORT", -1));
    }

    @Test
    void zeroMaxAttemptsRejected() {
        assertConstraintViolation(() -> jdbcTemplate.update("INSERT INTO platform.job (id, job_type, max_attempts) VALUES (?, ?, ?)", id(), "IMPORT", 0));
    }

    @Test
    void attemptCountExceedingMaxAttemptsRejected() {
        assertConstraintViolation(
                () -> jdbcTemplate.update("INSERT INTO platform.job (id, job_type, attempt_count, max_attempts) VALUES (?, ?, ?, ?)", id(), "IMPORT", 6, 5));
    }

    // -----------------------------------------------------------------------
    // Cascade: deleting a user removes auth identities, sessions, events, jobs
    // -----------------------------------------------------------------------

    @Test
    void deletingUserCascadesToAllRelatedRows() {
        var userId = id();
        var future = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);

        jdbcTemplate.update("INSERT INTO identity.user_account (id, email, email_normalized) VALUES (?, ?, ?)", userId, "frank@example.com",
                "frank@example.com");
        jdbcTemplate.update("INSERT INTO identity.auth_identity (id, user_account_id, provider, provider_subject) VALUES (?, ?, ?, ?)", id(), userId, "LOCAL",
                "frank@example.com");
        jdbcTemplate.update(
                "INSERT INTO identity.device_session" + " (id, user_account_id, family_id, refresh_token_hash, expires_at)" + " VALUES (?, ?, ?, ?, ?)", id(),
                userId, id(), "cascade:" + UUID.randomUUID(), future);
        jdbcTemplate.update("INSERT INTO platform.security_event (id, user_account_id, event_type, occurred_at) VALUES (?, ?, ?, ?)", id(), userId, "LOGIN",
                OffsetDateTime.now(ZoneOffset.UTC));
        jdbcTemplate.update("INSERT INTO platform.job (id, owner_user_account_id, job_type) VALUES (?, ?, ?)", id(), userId, "IMPORT");

        jdbcTemplate.update("DELETE FROM identity.user_account WHERE id = ?", userId);

        assertThat(count("SELECT COUNT(*) FROM identity.auth_identity WHERE user_account_id = ?", userId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM identity.device_session WHERE user_account_id = ?", userId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM platform.security_event WHERE user_account_id = ?", userId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM platform.job WHERE owner_user_account_id = ?", userId)).isZero();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private UUID id() {
        return UUID.randomUUID();
    }

    private int count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    /**
     * Runs {@code block} in an isolated REQUIRES_NEW transaction and asserts it throws a constraint violation.
     */
    private void assertConstraintViolation(Runnable block) {
        var template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThatThrownBy(() -> template.execute(status -> {
            block.run();
            return null;
        })).isInstanceOf(DataIntegrityViolationException.class);
    }
}
