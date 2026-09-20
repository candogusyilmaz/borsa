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
import dev.canverse.stocks.testing.DatabaseCleaner;
import dev.canverse.stocks.testing.IdentityTestPropertiesConfiguration;
import dev.canverse.stocks.testing.IntegrationTest;
import dev.canverse.stocks.testing.TestClock;
import dev.canverse.stocks.testing.TestClockConfiguration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
@IntegrationTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {"stocks.identity.refresh-session.lifetime=30d"})
@Import({IdentityTestPropertiesConfiguration.class, TestClockConfiguration.class})
class DeviceSessionRevocationServiceTest {

    @Autowired
    DatabaseCleaner databaseCleaner;
    @Autowired
    TestClock testClock;
    private static final Instant T0 = Instant.parse("2026-08-15T12:00:00Z");
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
        testClock.setInstant(T0);

        databaseCleaner.resetApplicationState();
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

}
