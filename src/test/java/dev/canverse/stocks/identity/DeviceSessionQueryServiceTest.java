package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.canverse.stocks.identity.application.DeviceSessionQueryService;
import dev.canverse.stocks.identity.application.LocalAccountRegistrationService;
import dev.canverse.stocks.identity.application.RefreshSessionIssuanceService;
import dev.canverse.stocks.identity.application.RefreshSessionRotationService;
import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.identity.infrastructure.SecureRefreshTokenGenerator;
import dev.canverse.stocks.identity.web.response.DeviceSessionStatus;
import dev.canverse.stocks.platform.error.AppException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "stocks.identity.refresh-session.lifetime=30d",
            "stocks.identity.access-token.issuer=https://issuer.test",
            "stocks.identity.access-token.audience=canverse-test-api",
            "stocks.identity.access-token.lifetime=5m",
            "stocks.identity.access-token.key-id=test-ephemeral"
        })
@Testcontainers
@Import(DeviceSessionQueryServiceTest.TestOverrides.class)
class DeviceSessionQueryServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-15T12:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    LocalAccountRegistrationService registrationService;

    @Autowired
    RefreshSessionIssuanceService issuanceService;

    @Autowired
    RefreshSessionRotationService rotationService;

    @Autowired
    SecureRefreshTokenGenerator refreshTokenGenerator;

    @Autowired
    DeviceSessionQueryService queryService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE platform.security_event, identity.device_session, identity.auth_identity, identity.user_account CASCADE");
    }

    @Test
    void aggregatesRotatedFamilyIntoSingleLogicalSession() {
        var userId = registrationService.register("user@example.com", "correct horse battery staple");
        var initial = issuanceService.issue(userId, "Chrome on Mac");

        // Rotate twice
        var rotated1 = rotationService.rotate(initial.refreshToken()).orElseThrow();
        var rotated2 = rotationService.rotate(rotated1.refreshToken()).orElseThrow();

        // Current session is rotated2
        var sessions = queryService.listSessions(userId, rotated2.sessionId());
        assertThat(sessions).hasSize(1);

        var family = sessions.getFirst();
        assertThat(family.familyId()).isEqualTo(initial.sessionId());
        assertThat(family.latestGenerationId()).isEqualTo(rotated2.sessionId());
        assertThat(family.deviceLabel()).isEqualTo("Chrome on Mac");
        assertThat(family.status()).isEqualTo(DeviceSessionStatus.ACTIVE);
        assertThat(family.current()).isTrue();
        assertThat(family.lastUsedAt()).isNotNull();

        // Detail lookup
        var detail = queryService.getSessionDetail(userId, rotated2.sessionId(), initial.sessionId());
        assertThat(detail.familyId()).isEqualTo(initial.sessionId());
        assertThat(detail.latestGenerationId()).isEqualTo(rotated2.sessionId());
        assertThat(detail.current()).isTrue();
    }

    @Test
    void completeListOrdersFamiliesByCreationAndIdAndPreservesInitialCreation() {
        var userId = registrationService.register("pageuser@example.com", "correct horse battery staple");

        var oldFamilyId = UUID.fromString("30000000-0000-4000-8000-000000000001");
        var tieLowFamilyId = UUID.fromString("30000000-0000-4000-8000-000000000002");
        var tieHighFamilyId = UUID.fromString("30000000-0000-4000-8000-000000000003");
        var latestFamilyId = UUID.fromString("30000000-0000-4000-8000-000000000004");
        var oldCreatedAt = T0.minus(Duration.ofHours(3));
        var tieCreatedAt = T0.minus(Duration.ofHours(2));
        var latestCreatedAt = T0.minus(Duration.ofHours(1));

        var oldSessionId = UUID.fromString("40000000-0000-4000-8000-000000000001");
        var oldToken = refreshTokenGenerator.generate();
        insertSession(oldSessionId, userId, oldFamilyId, oldToken.hash(), "old-device", oldCreatedAt);
        var rotatedSession = rotationService.rotate(oldToken.rawToken()).orElseThrow();

        insertSession(
                UUID.fromString("40000000-0000-4000-8000-000000000002"),
                userId,
                tieLowFamilyId,
                "tie-low-hash",
                "tie-low-device",
                tieCreatedAt);
        insertSession(
                UUID.fromString("40000000-0000-4000-8000-000000000003"),
                userId,
                tieHighFamilyId,
                "tie-high-hash",
                "tie-high-device",
                tieCreatedAt);
        insertSession(
                UUID.fromString("40000000-0000-4000-8000-000000000004"),
                userId,
                latestFamilyId,
                "latest-hash",
                "latest-device",
                latestCreatedAt);

        var sessions = queryService.listSessions(userId, rotatedSession.sessionId());

        assertThat(sessions).hasSize(4);
        assertThat(sessions).extracting(session -> session.familyId()).doesNotHaveDuplicates();
        assertThat(sessions)
                .extracting(session -> session.familyId())
                .containsExactly(latestFamilyId, tieHighFamilyId, tieLowFamilyId, oldFamilyId);
        assertThat(sessions)
                .extracting(session -> session.createdAt())
                .containsExactly(latestCreatedAt, tieCreatedAt, tieCreatedAt, oldCreatedAt);
        assertThat(sessions).extracting(session -> session.current()).containsExactly(false, false, false, true);
        assertThat(sessions)
                .extracting(session -> session.status())
                .containsExactly(
                        DeviceSessionStatus.ACTIVE,
                        DeviceSessionStatus.ACTIVE,
                        DeviceSessionStatus.ACTIVE,
                        DeviceSessionStatus.ACTIVE);
        assertThat(sessions.getLast().latestGenerationId()).isEqualTo(rotatedSession.sessionId());
        assertThat(sessions.getLast().deviceLabel()).isEqualTo("old-device");
    }

    @Test
    void ownerScopedQueriesPreventCrossOwnerAccess() {
        var user1 = registrationService.register("user1@example.com", "correct horse battery staple");
        var user2 = registrationService.register("user2@example.com", "correct horse battery staple");

        var session1 = issuanceService.issue(user1, "device1");
        var session2 = issuanceService.issue(user2, "device2");

        // User 1 lists sessions -> sees only session1
        var sessions1 = queryService.listSessions(user1, session1.sessionId());
        assertThat(sessions1).hasSize(1);
        assertThat(sessions1.getFirst().familyId()).isEqualTo(session1.sessionId());

        // User 1 attempts detail of user 2's session -> 404
        assertThatThrownBy(() -> queryService.getSessionDetail(user1, session1.sessionId(), session2.sessionId()))
                .isInstanceOf(AppException.class)
                .satisfies(e ->
                        assertThat(((AppException) e).getErrorCode()).isEqualTo(IdentityErrorCode.SESSION_NOT_FOUND));
    }

    @Test
    void familyListAndDetailExecuteExactlyOneSqlStatement() {
        var userId = registrationService.register("bounded@example.com", "correct horse battery staple");
        var session1 = issuanceService.issue(userId, "device1");
        var session2 = issuanceService.issue(userId, "device2");
        var session3 = issuanceService.issue(userId, "device3");

        // Rotate several times to build multi-generation families
        rotationService.rotate(session1.refreshToken());
        rotationService.rotate(session2.refreshToken());

        // Bounded list query count check
        var statementsBeforeList = executedStatements.get();
        var sessions = queryService.listSessions(userId, session1.sessionId());
        var statementsAfterList = executedStatements.get();

        assertThat(sessions).hasSize(3);
        assertThat(statementsAfterList - statementsBeforeList).isEqualTo(1L);

        // Bounded detail query count check
        var statementsBeforeDetail = executedStatements.get();
        var detail = queryService.getSessionDetail(userId, session1.sessionId(), session1.sessionId());
        var statementsAfterDetail = executedStatements.get();

        assertThat(detail).isNotNull();
        assertThat(statementsAfterDetail - statementsBeforeDetail).isEqualTo(1L);
    }

    private static final AtomicLong executedStatements = new AtomicLong();

    private void insertSession(
            UUID sessionId,
            UUID userId,
            UUID familyId,
            String refreshTokenHash,
            String deviceLabel,
            Instant createdAt) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> jdbcTemplate.update(
                        "INSERT INTO identity.device_session"
                                + " (id, user_account_id, family_id, refresh_token_hash, device_label, created_at, expires_at)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                        sessionId,
                        userId,
                        familyId,
                        refreshTokenHash,
                        deviceLabel,
                        OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC),
                        OffsetDateTime.ofInstant(T0.plus(Duration.ofDays(30)), ZoneOffset.UTC)));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOverrides {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(T0, ZoneOffset.UTC);
        }

        @Bean
        static org.springframework.beans.factory.config.BeanPostProcessor dataSourceQueryCountingPostProcessor() {
            return new org.springframework.beans.factory.config.BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof javax.sql.DataSource dataSource) {
                        return java.lang.reflect.Proxy.newProxyInstance(
                                javax.sql.DataSource.class.getClassLoader(),
                                new Class<?>[] {javax.sql.DataSource.class},
                                (proxy, method, args) -> {
                                    if ("getConnection".equals(method.getName())) {
                                        var connection = (java.sql.Connection) method.invoke(dataSource, args);
                                        return java.lang.reflect.Proxy.newProxyInstance(
                                                java.sql.Connection.class.getClassLoader(),
                                                new Class<?>[] {java.sql.Connection.class},
                                                (connProxy, connMethod, connArgs) -> {
                                                    if ("prepareStatement".equals(connMethod.getName())
                                                            || "createStatement".equals(connMethod.getName())) {
                                                        executedStatements.incrementAndGet();
                                                    }
                                                    return connMethod.invoke(connection, connArgs);
                                                });
                                    }
                                    return method.invoke(dataSource, args);
                                });
                    }
                    return bean;
                }
            };
        }
    }
}
