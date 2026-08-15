package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.canverse.stocks.identity.application.AuthenticationAbuseProtection;
import dev.canverse.stocks.identity.application.DeviceSessionRevocationService;
import dev.canverse.stocks.identity.application.LocalAccountRegistrationService;
import dev.canverse.stocks.identity.application.LocalLoginAttemptService;
import dev.canverse.stocks.identity.application.LocalLoginService;
import dev.canverse.stocks.identity.application.LocalRegistrationAttemptService;
import dev.canverse.stocks.identity.application.RefreshSessionIssuanceService;
import dev.canverse.stocks.identity.application.RefreshSessionRotationService;
import dev.canverse.stocks.identity.domain.DeviceSession;
import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.identity.infrastructure.DeviceSessionRepository;
import dev.canverse.stocks.platform.application.SecurityEventRecorder;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.infrastructure.SecurityEventRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
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
@Execution(ExecutionMode.SAME_THREAD)
@Import(IdentitySecurityEventIntegrationTest.TestOverrides.class)
class IdentitySecurityEventIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-08-15T12:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    LocalAccountRegistrationService registrationService;

    @Autowired
    LocalLoginAttemptService loginAttemptService;

    @Autowired
    RefreshSessionIssuanceService issuanceService;

    @Autowired
    RefreshSessionRotationService rotationService;

    @Autowired
    DeviceSessionRevocationService revocationService;

    @Autowired
    SecurityEventRecorder securityEventRecorder;

    @MockitoSpyBean
    SecurityEventRepository securityEventRepository;

    @Autowired
    LocalLoginService localLoginService;

    @Autowired
    LocalRegistrationAttemptService registrationAttemptService;

    @Autowired
    AuthenticationAbuseProtection abuseProtection;

    @Autowired
    DeviceSessionRepository sessionRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE platform.security_event, identity.device_session, identity.auth_identity, identity.user_account CASCADE");
        entityManager.clear();
        org.mockito.Mockito.reset(securityEventRepository);
    }

    @Test
    void successfulLoginRecordsLocalLoginSucceededSecurityEvent() {
        var userId = registrationService.register("event-login@example.com", "correct horse battery staple");

        var result = loginAttemptService.attemptLogin(
                "event-login@example.com", "correct horse battery staple", "laptop", "127.0.0.1", "trace-login-1");

        var events = securityEventRepository.findAll().stream()
                .filter(e ->
                        e.getUserAccount() != null && e.getUserAccount().getId().equals(userId))
                .toList();
        assertThat(events).hasSize(1);

        var event = events.getFirst();
        assertThat(event.getEventType()).isEqualTo(SecurityEventRecorder.LOCAL_LOGIN_SUCCEEDED);
        assertThat(event.getUserAccount()).isNotNull();
        assertThat(event.getUserAccount().getId()).isEqualTo(userId);
        assertThat(event.getDetails()).contains(result.sessionId().toString());
    }

    @Test
    void failedLoginRecordsAnonymousSecurityEvent() {
        registrationService.register("failed-login@example.com", "correct horse battery staple");

        assertThatThrownBy(() -> loginAttemptService.attemptLogin(
                        "failed-login@example.com", "wrong-password", "laptop", "127.0.0.1", "trace-fail-1"))
                .isInstanceOf(AppException.class)
                .satisfies(e ->
                        assertThat(((AppException) e).getErrorCode()).isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS));

        var events = securityEventRepository.findAll().stream()
                .filter(e -> e.getUserAccount() == null && e.getDetails().contains("trace-fail-1"))
                .toList();
        assertThat(events).hasSize(1);

        var event = events.getFirst();
        assertThat(event.getEventType()).isEqualTo(SecurityEventRecorder.LOCAL_LOGIN_FAILED);
        assertThat(event.getUserAccount()).isNull();
        assertThat(event.getDetails()).contains("trace-fail-1");
        assertThat(event.getDetails()).contains("LOGIN");
    }

    @Test
    void throttledLoginRecordsThrottledAnonymousSecurityEvent() {
        registrationService.register("throttled@example.com", "correct horse battery staple");

        // Fail 5 times to trigger throttle
        for (int i = 0; i < 5; i++) {
            try {
                loginAttemptService.attemptLogin(
                        "throttled@example.com", "wrong", "laptop", "127.0.0.1", "trace-t-" + i);
            } catch (AppException ignored) {
            }
        }

        // 6th attempt is throttled before login execution
        assertThatThrownBy(() -> loginAttemptService.attemptLogin(
                        "throttled@example.com", "correct horse battery staple", "laptop", "127.0.0.1", "trace-t-6"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(IdentityErrorCode.AUTHENTICATION_THROTTLED));

        var throttledEvents = securityEventRepository.findAll().stream()
                .filter(e -> SecurityEventRecorder.LOCAL_LOGIN_THROTTLED.equals(e.getEventType()))
                .toList();
        assertThat(throttledEvents).isNotEmpty();
    }

    @Test
    void refreshReuseDetectedRecordsSecurityEventAndRevokesTerminal() {
        var userId = registrationService.register("reuse-events@example.com", "correct horse battery staple");
        var initial = issuanceService.issue(userId, "laptop");

        // Rotate generation 1 -> 2
        var rotated = rotationService.rotate(initial.refreshToken()).orElseThrow();

        // Replay generation 1 (reuse!)
        var reuseResult = rotationService.rotate(initial.refreshToken());
        assertThat(reuseResult).isEmpty();

        var events = securityEventRepository.findAll().stream()
                .filter(e ->
                        e.getUserAccount() != null && e.getUserAccount().getId().equals(userId))
                .toList();
        assertThat(events).hasSize(1);

        var event = events.getFirst();
        assertThat(event.getEventType()).isEqualTo(SecurityEventRecorder.REFRESH_REUSE_DETECTED);
        assertThat(event.getUserAccount()).isNotNull();
        assertThat(event.getUserAccount().getId()).isEqualTo(userId);
        assertThat(event.getDetails()).contains(initial.sessionId().toString());

        // Active generation is revoked with REUSE_DETECTED
        var terminal = sessionRepository.findById(rotated.sessionId()).orElseThrow();
        assertThat(terminal.getRevokeReason()).isEqualTo(DeviceSession.REUSE_DETECTED_REVOKE_REASON);
    }

    @Test
    void logoutAndRevocationRecordSafeEvents() {
        var userId = registrationService.register("logout-events@example.com", "correct horse battery staple");
        var session1 = issuanceService.issue(userId, "d1");
        var session2 = issuanceService.issue(userId, "d2");

        // Revoke selected session1
        revocationService.revokeSelectedFamily(userId, session1.sessionId(), session1.sessionId());

        // Logout all remaining
        revocationService.logoutAllSessions(userId);

        var events = securityEventRepository.findAll().stream()
                .filter(e ->
                        e.getUserAccount() != null && e.getUserAccount().getId().equals(userId))
                .toList();
        assertThat(events).hasSize(2);

        var e1 = events.stream()
                .filter(e -> e.getEventType().equals(SecurityEventRecorder.DEVICE_SESSION_REVOKED))
                .findFirst()
                .orElseThrow();
        assertThat(e1.getDetails()).contains(session1.sessionId().toString());

        var e2 = events.stream()
                .filter(e -> e.getEventType().equals(SecurityEventRecorder.ALL_SESSIONS_LOGGED_OUT))
                .findFirst()
                .orElseThrow();
        assertThat(e2.getDetails()).contains("revokedFamilyCount");
    }

    @Test
    void securityEventRecorderRejectsMismatchedScopesAndInvalidTypes() {
        var userId = registrationService.register("scope-check@example.com", "correct horse battery staple");

        // Anonymous recording rejects user-scoped event type
        assertThatThrownBy(() -> securityEventRecorder.recordAnonymousRequiresNew(
                        SecurityEventRecorder.LOCAL_LOGIN_SUCCEEDED,
                        Map.of(
                                "sessionId",
                                UUID.randomUUID().toString(),
                                "familyId",
                                UUID.randomUUID().toString())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not an anonymous event type");

        // User-scoped recording rejects anonymous event type
        assertThatThrownBy(() -> securityEventRecorder.record(
                        userId,
                        SecurityEventRecorder.LOCAL_LOGIN_FAILED,
                        Map.of("traceId", "trace-1", "operation", "LOGIN")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a user-scoped event type");

        // Rejects negative revokedFamilyCount
        assertThatThrownBy(() -> securityEventRecorder.record(
                        userId, SecurityEventRecorder.ALL_SESSIONS_LOGGED_OUT, Map.of("revokedFamilyCount", -1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative number");

        // Rejects non-string traceId
        assertThatThrownBy(() -> securityEventRecorder.recordAnonymousRequiresNew(
                        SecurityEventRecorder.LOCAL_LOGIN_FAILED, Map.of("traceId", 12345, "operation", "LOGIN")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be a non-blank string");
    }

    @Test
    void eventPersistenceFailureRollsBackSessionCreation() {
        var email = "event-fail-login@example.com";
        var password = "correct horse battery staple";
        registrationService.register(email, password);

        org.mockito.Mockito.doThrow(new RuntimeException("Simulated security event persistence failure"))
                .when(securityEventRepository)
                .save(org.mockito.ArgumentMatchers.any(dev.canverse.stocks.platform.domain.SecurityEvent.class));

        assertThatThrownBy(() -> localLoginService.login(email, password, "laptop"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Simulated security event persistence failure");

        // The session creation was rolled back
        assertThat(sessionRepository.count()).isZero();
    }

    @Test
    void eventPersistenceFailureRollsBackSessionRevocation() {
        var email = "event-fail-revocation@example.com";
        var password = "correct horse battery staple";
        var userId = registrationService.register(email, password);
        var session = issuanceService.issue(userId, "laptop");

        org.mockito.Mockito.doThrow(new RuntimeException("Simulated security event persistence failure"))
                .when(securityEventRepository)
                .save(org.mockito.ArgumentMatchers.any(dev.canverse.stocks.platform.domain.SecurityEvent.class));

        assertThatThrownBy(() -> revocationService.logoutCurrentSession(userId, session.sessionId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Simulated security event persistence failure");

        // Session was NOT revoked
        var activeSession = sessionRepository.findById(session.sessionId()).orElseThrow();
        assertThat(activeSession.getRevokedAt()).isNull();
    }

    @Test
    void throttleEventPersistenceFailureDoesNotLeaveBucketBlocked() {
        var email = "throttle-fail@example.com";
        var password = "correct horse battery staple";
        registrationService.register(email, password);

        // Fail 4 times (limit is 5)
        for (int i = 0; i < 4; i++) {
            try {
                loginAttemptService.attemptLogin(email, "wrong", "laptop", "10.10.10.10", "trace-" + i);
            } catch (AppException ignored) {
            }
        }

        // Mock failure on 5th attempt when trying to save the LOCAL_LOGIN_THROTTLED event
        org.mockito.Mockito.doThrow(new RuntimeException("Throttled event save failure"))
                .when(securityEventRepository)
                .saveAndFlush(org.mockito.ArgumentMatchers.argThat(
                        event -> SecurityEventRecorder.LOCAL_LOGIN_THROTTLED.equals(event.getEventType())));

        // 5th attempt triggers throttle, but event persistence fails and rolls back the throttle in-memory
        assertThatThrownBy(() -> loginAttemptService.attemptLogin(email, "wrong", "laptop", "10.10.10.10", "trace-5"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Throttled event save failure");

        // Bucket was rolled back and is NOT left blocked: checkLoginAllowed is still ALLOWED
        assertThat(abuseProtection.checkLoginAllowed(email, "10.10.10.10"))
                .isEqualTo(AuthenticationAbuseProtection.CheckResult.ALLOWED);

        // Subsequent valid login is NOT blocked with 429 and succeeds!
        org.mockito.Mockito.reset(securityEventRepository);
        var result = loginAttemptService.attemptLogin(email, password, "laptop", "10.10.10.10", "trace-6");
        assertThat(result).isNotNull();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOverrides {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(T0, ZoneOffset.UTC);
        }
    }
}
