package dev.canverse.stocks;

import static org.assertj.core.api.Assertions.assertThat;

import dev.canverse.stocks.identity.AuthIdentityRepository;
import dev.canverse.stocks.identity.DeviceSessionRepository;
import dev.canverse.stocks.identity.UserAccountRepository;
import dev.canverse.stocks.platform.JobRepository;
import dev.canverse.stocks.platform.SecurityEventRepository;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Transactional
class EntityMappingTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    private static final OffsetDateTime T1 = OffsetDateTime.of(2026, 8, 8, 9, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime T2 = OffsetDateTime.of(2026, 8, 8, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime T3 = OffsetDateTime.of(2026, 8, 8, 11, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManager entityManager;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    AuthIdentityRepository authIdentityRepository;

    @Autowired
    DeviceSessionRepository deviceSessionRepository;

    @Autowired
    SecurityEventRepository securityEventRepository;

    @Autowired
    JobRepository jobRepository;

    @Test
    void userAccountCanBeLoaded() {
        var userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO identity.user_account (id, email, email_normalized, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?)",
                userId,
                "Alice@example.com",
                "alice@example.com",
                T1,
                T2);

        entityManager.clear();

        var account = userAccountRepository.findById(userId).orElseThrow();

        assertThat(account.getId()).isEqualTo(userId);
        assertThat(account.getEmail()).isEqualTo("Alice@example.com");
        assertThat(account.getEmailNormalized()).isEqualTo("alice@example.com");
        assertThat(account.getDisabledAt()).isNull();
        assertThat(account.getCreatedAt()).isEqualTo(T1.toInstant());
        assertThat(account.getUpdatedAt()).isEqualTo(T2.toInstant());
    }

    @Test
    void authIdentityCanBeLoadedWithUserAccountReference() {
        var userId = UUID.randomUUID();
        var authId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO identity.user_account (id, email, email_normalized, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?)",
                userId,
                "bob@example.com",
                "bob@example.com",
                T1,
                T1);
        jdbcTemplate.update(
                "INSERT INTO identity.auth_identity"
                        + " (id, user_account_id, provider, provider_subject, password_hash, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                authId,
                userId,
                "LOCAL",
                "bob@example.com",
                "$2a$12$hashedpassword",
                T1,
                T1);

        entityManager.clear();

        var authIdentity = authIdentityRepository.findById(authId).orElseThrow();

        assertThat(authIdentity.getId()).isEqualTo(authId);
        assertThat(authIdentity.getProvider()).isEqualTo("LOCAL");
        assertThat(authIdentity.getProviderSubject()).isEqualTo("bob@example.com");
        assertThat(authIdentity.getPasswordHash()).isEqualTo("$2a$12$hashedpassword");
        assertThat(authIdentity.getUserAccount().getId()).isEqualTo(userId);
    }

    @Test
    void deviceSessionCanBeLoadedWithUserAccountReference() {
        var userId = UUID.randomUUID();
        var sessionId = UUID.randomUUID();
        var familyId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO identity.user_account (id, email, email_normalized, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?)",
                userId,
                "carol@example.com",
                "carol@example.com",
                T1,
                T1);
        jdbcTemplate.update(
                "INSERT INTO identity.device_session"
                        + " (id, user_account_id, family_id, refresh_token_hash, created_at, expires_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                sessionId,
                userId,
                familyId,
                "token-hash-carol",
                T1,
                T3);

        entityManager.clear();

        var session = deviceSessionRepository.findById(sessionId).orElseThrow();

        assertThat(session.getFamilyId()).isEqualTo(familyId);
        assertThat(session.getRefreshTokenHash()).isEqualTo("token-hash-carol");
        assertThat(session.getCreatedAt()).isEqualTo(T1.toInstant());
        assertThat(session.getExpiresAt()).isEqualTo(T3.toInstant());
        assertThat(session.getLastUsedAt()).isNull();
        assertThat(session.getRevokedAt()).isNull();
        assertThat(session.getReplacedBySessionId()).isNull();
        assertThat(session.getUserAccount().getId()).isEqualTo(userId);
    }

    @Test
    void anonymousSecurityEventCanBeLoaded() {
        var eventId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO platform.security_event (id, event_type, occurred_at, details)"
                        + " VALUES (?, ?, ?, CAST(? AS jsonb))",
                eventId,
                "LOGIN_FAILED",
                T1,
                "{\"reason\": \"bad_password\"}");

        entityManager.clear();

        var event = securityEventRepository.findById(eventId).orElseThrow();

        assertThat(event.getEventType()).isEqualTo("LOGIN_FAILED");
        assertThat(event.getOccurredAt()).isEqualTo(T1.toInstant());
        assertThat(event.getUserAccount()).isNull();
        assertThat(event.getDetails()).contains("bad_password");
    }

    @Test
    void jobCanBeLoaded() {
        var jobId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO platform.job"
                        + " (id, job_type, status, payload, available_at, attempt_count, max_attempts, created_at, updated_at)"
                        + " VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?)",
                jobId,
                "IMPORT_CSV",
                "READY",
                "{\"file\": \"stocks.csv\"}",
                T1,
                0,
                5,
                T1,
                T1);

        entityManager.clear();

        var job = jobRepository.findById(jobId).orElseThrow();

        assertThat(job.getJobType()).isEqualTo("IMPORT_CSV");
        assertThat(job.getStatus()).isEqualTo("READY");
        assertThat(job.getAttemptCount()).isZero();
        assertThat(job.getMaxAttempts()).isEqualTo(5);
        assertThat(job.getAvailableAt()).isEqualTo(T1.toInstant());
        assertThat(job.getPayload()).contains("stocks.csv");
        assertThat(job.getClaimedBy()).isNull();
        assertThat(job.getClaimToken()).isNull();
        assertThat(job.getCompletedAt()).isNull();
    }
}
