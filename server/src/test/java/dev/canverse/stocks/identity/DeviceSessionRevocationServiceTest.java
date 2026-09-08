package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.canverse.stocks.identity.application.AccessTokenIssuanceService;
import dev.canverse.stocks.identity.application.DeviceSessionRevocationService;
import dev.canverse.stocks.identity.application.LocalAccessTokenAuthenticationConverter;
import dev.canverse.stocks.identity.application.LocalAccountRegistrationService;
import dev.canverse.stocks.identity.application.RefreshSessionIssuanceService;
import dev.canverse.stocks.identity.application.RefreshSessionRotationService;
import dev.canverse.stocks.identity.domain.DeviceSession;
import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.identity.infrastructure.DeviceSessionRepository;
import dev.canverse.stocks.platform.error.AppException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"stocks.identity.refresh-session.lifetime=30d", "stocks.identity.access-token.issuer=https://issuer.test",
                "stocks.identity.access-token.audience=canverse-test-api", "stocks.identity.access-token.lifetime=5m",
                "stocks.identity.access-token.key-id=test-ephemeral"})
@Testcontainers
@Import(DeviceSessionRevocationServiceTest.TestOverrides.class)
class DeviceSessionRevocationServiceTest {

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
    AccessTokenIssuanceService accessTokenIssuanceService;

    @Autowired
    DeviceSessionRevocationService revocationService;

    @Autowired
    LocalAccessTokenAuthenticationConverter tokenConverter;

    @Autowired
    JwtDecoder jwtDecoder;

    @Autowired
    DeviceSessionRepository sessionRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE platform.security_event, identity.device_session, identity.auth_identity, identity.user_account CASCADE");
    }

    @Test
    void currentSessionLogoutRevokesTerminalGenerationAndFailsCredentials() {
        var userId = registrationService.register("currentlogout@example.com", "correct horse battery staple");
        var initial = issuanceService.issue(userId, "laptop");
        var rotated = rotationService.rotate(initial.refreshToken()).orElseThrow();
        var accessToken = rotated.accessToken();

        // Logout current session (using rotated.sessionId())
        revocationService.logoutCurrentSession(userId, rotated.sessionId());

        // Verify initial generation (historical) is still ROTATED
        var initialGen = sessionRepository.findById(initial.sessionId()).orElseThrow();
        assertThat(initialGen.getRevokeReason()).isEqualTo(DeviceSession.ROTATED_REVOKE_REASON);

        // Verify terminal generation is USER_LOGOUT
        var terminalGen = sessionRepository.findById(rotated.sessionId()).orElseThrow();
        assertThat(terminalGen.getRevokeReason()).isEqualTo(DeviceSession.USER_LOGOUT_REVOKE_REASON);
        assertThat(terminalGen.getRevokedAt()).isEqualTo(T0);

        // Verify access token fails bearer conversion
        var jwt = jwtDecoder.decode(accessToken);
        assertThatThrownBy(() -> tokenConverter.convert(jwt)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void selectedFamilyRevocationIsIdempotentAndRejectsCrossOwner() {
        var user1 = registrationService.register("user1@example.com", "correct horse battery staple");
        var user2 = registrationService.register("user2@example.com", "correct horse battery staple");

        var session1 = issuanceService.issue(user1, "device1");
        var session2 = issuanceService.issue(user2, "device2");

        // Revoke session1 by selected family ID
        var isCurrent = revocationService.revokeSelectedFamily(user1, session1.sessionId(), session1.sessionId());
        assertThat(isCurrent).isTrue();

        // Repeated revocation of already ended family is idempotent
        var isCurrentAgain = revocationService.revokeSelectedFamily(user1, session1.sessionId(), session1.sessionId());
        assertThat(isCurrentAgain).isTrue();

        // Cross-owner revocation throws SESSION_NOT_FOUND without mutating user2's
        // session
        assertThatThrownBy(() -> revocationService.revokeSelectedFamily(user1, session1.sessionId(), session2.sessionId())).isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo(IdentityErrorCode.SESSION_NOT_FOUND));

        var user2Session = sessionRepository.findById(session2.sessionId()).orElseThrow();
        assertThat(user2Session.getRevokedAt()).isNull();
    }

    @Test
    void allSessionsLogoutRevokesEveryActiveFamilyForUser() {
        var userId = registrationService.register("alllogout@example.com", "correct horse battery staple");
        var session1 = issuanceService.issue(userId, "device1");
        var session2 = issuanceService.issue(userId, "device2");
        var session3 = issuanceService.issue(userId, "device3");

        // Already revoke session3 individually first
        revocationService.revokeSelectedFamily(userId, session1.sessionId(), session3.sessionId());

        // Call logoutAllSessions
        revocationService.logoutAllSessions(userId);

        var s1 = sessionRepository.findById(session1.sessionId()).orElseThrow();
        var s2 = sessionRepository.findById(session2.sessionId()).orElseThrow();
        var s3 = sessionRepository.findById(session3.sessionId()).orElseThrow();

        assertThat(s1.getRevokeReason()).isEqualTo(DeviceSession.USER_LOGOUT_ALL_REVOKE_REASON);
        assertThat(s2.getRevokeReason()).isEqualTo(DeviceSession.USER_LOGOUT_ALL_REVOKE_REASON);
        // s3 retains its original USER_REVOKED reason
        assertThat(s3.getRevokeReason()).isEqualTo(DeviceSession.USER_REVOKED_REVOKE_REASON);
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
