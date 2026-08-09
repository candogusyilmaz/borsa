package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import dev.canverse.stocks.identity.application.IssuedRefreshSession;
import dev.canverse.stocks.identity.application.LocalAccountRegistrationService;
import dev.canverse.stocks.identity.application.RefreshSessionAuthenticationService;
import dev.canverse.stocks.identity.application.RefreshSessionIssuanceService;
import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.identity.infrastructure.DeviceSessionRepository;
import dev.canverse.stocks.identity.infrastructure.SecureRefreshTokenGenerator;
import dev.canverse.stocks.platform.error.AppException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
        properties = "stocks.identity.refresh-session.lifetime=2h")
@Testcontainers
@Import(RefreshSessionAuthenticationServiceTest.TestOverrides.class)
class RefreshSessionAuthenticationServiceTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-08-09T12:00:00.750Z");
    private static final Instant DISABLED_AT = OBSERVED_AT.minus(Duration.ofMinutes(30));
    private static final String RAW_PASSWORD = "correct horse battery staple";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    LocalAccountRegistrationService registrationService;

    @Autowired
    RefreshSessionIssuanceService issuanceService;

    @Autowired
    RefreshSessionAuthenticationService authenticationService;

    @Autowired
    SecureRefreshTokenGenerator refreshTokenGenerator;

    @Autowired
    DeviceSessionRepository deviceSessionRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearIdentityTables() {
        runInTransaction(() -> {
            jdbcTemplate.update("DELETE FROM identity.device_session");
            jdbcTemplate.update("DELETE FROM identity.auth_identity");
            jdbcTemplate.update("DELETE FROM identity.user_account");
        });
    }

    @Test
    void activeRefreshTokenAuthenticatesOnlyItsSessionWithoutWrites() {
        var issued = registerAndIssue("active-refresh@example.com", "Active device");
        var persistedBefore =
                deviceSessionRepository.findById(issued.sessionId()).orElseThrow();
        var identityBeforeAuthentication = snapshot();

        var authenticatedSessionId = authenticationService.authenticate(issued.refreshToken());

        assertThat(authenticatedSessionId).isEqualTo(issued.sessionId());
        assertThat(persistedBefore.getRefreshTokenHash()).isEqualTo(refreshTokenGenerator.hash(issued.refreshToken()));
        assertThat(persistedBefore.getLastUsedAt()).isNull();
        assertThat(snapshot()).isEqualTo(identityBeforeAuthentication);
        assertThat(rawTokenTextColumnOccurrences(issued.refreshToken())).isZero();
    }

    @Test
    void unknownRevokedExpiredAndDisabledCredentialsFailUniformlyWithoutWrites() {
        var unknownRawToken = "unknown opaque refresh token";
        var revoked = registerAndIssue("revoked-refresh@example.com", "Revoked device");
        var expired = registerAndIssue("expired-refresh@example.com", "Expired device");
        var disabled = registerAndIssue("disabled-refresh@example.com", "Disabled device");
        var disabledUserId = deviceSessionRepository
                .findById(disabled.sessionId())
                .orElseThrow()
                .getUserAccount()
                .getId();
        runInTransaction(() -> {
            jdbcTemplate.update(
                    "UPDATE identity.device_session SET revoked_at = ?, revoke_reason = ? WHERE id = ?",
                    OBSERVED_AT.atOffset(ZoneOffset.UTC),
                    "test fixture revocation",
                    revoked.sessionId());
            jdbcTemplate.update(
                    "UPDATE identity.device_session SET created_at = ?, expires_at = ? WHERE id = ?",
                    OBSERVED_AT.minus(Duration.ofHours(1)).atOffset(ZoneOffset.UTC),
                    OBSERVED_AT.atOffset(ZoneOffset.UTC),
                    expired.sessionId());
            jdbcTemplate.update(
                    "UPDATE identity.user_account SET disabled_at = ? WHERE id = ?",
                    DISABLED_AT.atOffset(ZoneOffset.UTC),
                    disabledUserId);
        });
        var identityBeforeAuthentication = snapshot();

        var unknownFailure = authenticateRejected(unknownRawToken, identityBeforeAuthentication);
        var revokedFailure = authenticateRejected(revoked.refreshToken(), identityBeforeAuthentication);
        var expiredFailure = authenticateRejected(expired.refreshToken(), identityBeforeAuthentication);
        var disabledFailure = authenticateRejected(disabled.refreshToken(), identityBeforeAuthentication);

        var failures = List.of(unknownFailure, revokedFailure, expiredFailure, disabledFailure);
        assertThat(failures)
                .extracting(AppException::getMessage)
                .containsOnly(IdentityErrorCode.INVALID_CREDENTIALS.getDescription());
        var sensitiveValues = List.of(
                unknownRawToken,
                revoked.refreshToken(),
                expired.refreshToken(),
                disabled.refreshToken(),
                refreshTokenGenerator.hash(unknownRawToken),
                refreshTokenGenerator.hash(revoked.refreshToken()),
                refreshTokenGenerator.hash(expired.refreshToken()),
                refreshTokenGenerator.hash(disabled.refreshToken()),
                revoked.sessionId().toString(),
                expired.sessionId().toString(),
                disabled.sessionId().toString(),
                disabledUserId.toString(),
                "test fixture revocation");
        failures.forEach(failure -> assertThat(failure.toString()).doesNotContain(sensitiveValues));
    }

    private IssuedRefreshSession registerAndIssue(String email, String deviceLabel) {
        var userId = registrationService.register(email, RAW_PASSWORD);
        return issuanceService.issue(userId, deviceLabel);
    }

    private AppException authenticateRejected(String rawRefreshToken, PersistedIdentityState expectedState) {
        var thrown = catchThrowable(() -> authenticationService.authenticate(rawRefreshToken));

        assertThat(thrown).isExactlyInstanceOf(AppException.class);
        var exception = (AppException) thrown;
        assertThat(exception.getErrorCode()).isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS);
        assertThat(exception.getParams()).isEmpty();
        assertThat(exception.getMessage()).isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS.getDescription());
        assertThat(snapshot()).isEqualTo(expectedState);
        return exception;
    }

    private long rawTokenTextColumnOccurrences(String rawToken) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM identity.device_session"
                        + " WHERE strpos(refresh_token_hash, ?) > 0"
                        + " OR strpos(coalesce(device_label, ''), ?) > 0"
                        + " OR strpos(coalesce(revoke_reason, ''), ?) > 0",
                Long.class,
                rawToken,
                rawToken,
                rawToken);
    }

    private PersistedIdentityState snapshot() {
        return new PersistedIdentityState(
                List.copyOf(jdbcTemplate.queryForList("SELECT * FROM identity.user_account ORDER BY id")),
                List.copyOf(jdbcTemplate.queryForList("SELECT * FROM identity.auth_identity ORDER BY id")),
                List.copyOf(jdbcTemplate.queryForList("SELECT * FROM identity.device_session ORDER BY id")));
    }

    private void runInTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOverrides {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(OBSERVED_AT, ZoneOffset.UTC);
        }
    }

    private record PersistedIdentityState(
            List<Map<String, Object>> users,
            List<Map<String, Object>> identities,
            List<Map<String, Object>> sessions) {}
}
