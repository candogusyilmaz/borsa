package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.canverse.stocks.identity.application.DeviceSessionQueryService;
import dev.canverse.stocks.identity.application.LocalAccountRegistrationService;
import dev.canverse.stocks.identity.application.RefreshSessionIssuanceService;
import dev.canverse.stocks.identity.application.RefreshSessionRotationService;
import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.identity.output.DeviceSessionStatus;
import dev.canverse.stocks.platform.error.AppException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.UUID;
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
    DeviceSessionQueryService queryService;

    @Autowired
    JdbcTemplate jdbcTemplate;

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
        var page = queryService.listSessions(userId, rotated2.sessionId(), 25, null);
        assertThat(page.sessions()).hasSize(1);

        var family = page.sessions().getFirst();
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
    void keysetPaginationReturnsPagesWithoutGapsOrDuplicates() {
        var userId = registrationService.register("pageuser@example.com", "correct horse battery staple");

        var familyIds = new ArrayList<UUID>();
        for (int i = 0; i < 7; i++) {
            var session = issuanceService.issue(userId, "device-" + i);
            familyIds.add(session.sessionId());
        }

        var currentSessionId = familyIds.getFirst();
        var collected = new ArrayList<UUID>();

        String cursor = null;
        int pageSize = 3;
        do {
            var page = queryService.listSessions(userId, currentSessionId, pageSize, cursor);
            for (var session : page.sessions()) {
                collected.add(session.familyId());
            }
            cursor = page.nextCursor();
        } while (cursor != null);

        assertThat(collected).hasSize(7);
        assertThat(collected).doesNotHaveDuplicates();
    }

    @Test
    void ownerScopedQueriesPreventCrossOwnerAccess() {
        var user1 = registrationService.register("user1@example.com", "correct horse battery staple");
        var user2 = registrationService.register("user2@example.com", "correct horse battery staple");

        var session1 = issuanceService.issue(user1, "device1");
        var session2 = issuanceService.issue(user2, "device2");

        // User 1 lists sessions -> sees only session1
        var page1 = queryService.listSessions(user1, session1.sessionId(), 25, null);
        assertThat(page1.sessions()).hasSize(1);
        assertThat(page1.sessions().getFirst().familyId()).isEqualTo(session1.sessionId());

        // User 1 attempts detail of user 2's session -> 404
        assertThatThrownBy(() -> queryService.getSessionDetail(user1, session1.sessionId(), session2.sessionId()))
                .isInstanceOf(AppException.class)
                .satisfies(e ->
                        assertThat(((AppException) e).getErrorCode()).isEqualTo(IdentityErrorCode.SESSION_NOT_FOUND));
    }

    @Test
    void rejectsInvalidSessionCursors() {
        var userId = registrationService.register("cursorfail@example.com", "correct horse battery staple");
        var session = issuanceService.issue(userId, "device");

        assertThatThrownBy(() -> queryService.listSessions(userId, session.sessionId(), 25, "not-a-valid-cursor"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(IdentityErrorCode.INVALID_SESSION_CURSOR));
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
        var page = queryService.listSessions(userId, session1.sessionId(), 25, null);
        var statementsAfterList = executedStatements.get();

        assertThat(page.sessions()).hasSize(3);
        assertThat(statementsAfterList - statementsBeforeList).isEqualTo(1L);

        // Bounded detail query count check
        var statementsBeforeDetail = executedStatements.get();
        var detail = queryService.getSessionDetail(userId, session1.sessionId(), session1.sessionId());
        var statementsAfterDetail = executedStatements.get();

        assertThat(detail).isNotNull();
        assertThat(statementsAfterDetail - statementsBeforeDetail).isEqualTo(1L);
    }

    private static final java.util.concurrent.atomic.AtomicLong executedStatements =
            new java.util.concurrent.atomic.AtomicLong();

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
