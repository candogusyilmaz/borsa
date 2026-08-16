package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import dev.canverse.stocks.identity.application.LocalAccountRegistrationService;
import dev.canverse.stocks.identity.application.RefreshSessionIssuanceService;
import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.identity.infrastructure.AuthIdentityRepository;
import dev.canverse.stocks.identity.infrastructure.DeviceSessionRepository;
import dev.canverse.stocks.identity.infrastructure.UserAccountRepository;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.testing.RecordingIdGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "stocks.identity.refresh-session.lifetime=12h")
@Testcontainers
@Import(RefreshSessionIssuanceServiceTest.TestOverrides.class)
class RefreshSessionIssuanceServiceTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-08-09T09:30:00Z");
    private static final Duration SESSION_LIFETIME = Duration.ofHours(12);
    private static final Instant DISABLED_AT = Instant.parse("2026-08-09T09:00:00Z");
    private static final String RAW_PASSWORD = "correct horse battery staple";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    LocalAccountRegistrationService registrationService;

    @Autowired
    RefreshSessionIssuanceService issuanceService;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    AuthIdentityRepository authIdentityRepository;

    @Autowired
    DeviceSessionRepository deviceSessionRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    RecordingIdGenerator idGenerator;

    @BeforeEach
    void clearIdentityTables() {
        runInTransaction(() -> {
            jdbcTemplate.update("DELETE FROM identity.device_session");
            jdbcTemplate.update("DELETE FROM identity.auth_identity");
            jdbcTemplate.update("DELETE FROM identity.user_account");
        });
        idGenerator.setNextIds();
    }

    @Test
    void eligibleUserCreatesOneInitialSessionContainingOnlyTheTokenHash() throws NoSuchAlgorithmException {
        var userId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        var authIdentityId = UUID.fromString("20000000-0000-0000-0000-000000000002");
        var sessionId = UUID.fromString("30000000-0000-0000-0000-000000000003");
        var deviceLabel = "Alice's laptop";
        idGenerator.setNextIds(userId, authIdentityId, sessionId);
        registrationService.register("alice@example.com", RAW_PASSWORD);

        var issued = issuanceService.issue(userId, deviceLabel);

        assertThat(issued.sessionId()).isEqualTo(sessionId);
        assertThat(issued.expiresAt()).isEqualTo(ISSUED_AT.plus(SESSION_LIFETIME));
        var persisted = deviceSessionRepository.findById(sessionId).orElseThrow();
        assertThat(persisted.getId()).isEqualTo(sessionId);
        assertThat(persisted.getUserAccount().getId()).isEqualTo(userId);
        assertThat(persisted.getFamilyId()).isEqualTo(sessionId);
        assertThat(persisted.getRefreshTokenHash()).isEqualTo(independentHash(issued.refreshToken()));
        assertThat(persisted.getRefreshTokenHash()).isNotEqualTo(issued.refreshToken());
        assertThat(persisted.getDeviceLabel()).isEqualTo(deviceLabel);
        assertThat(persisted.getCreatedAt()).isEqualTo(ISSUED_AT);
        assertThat(persisted.getExpiresAt()).isEqualTo(ISSUED_AT.plus(SESSION_LIFETIME));
        assertThat(persisted.getLastUsedAt()).isNull();
        assertThat(persisted.getRevokedAt()).isNull();
        assertThat(persisted.getRevokeReason()).isNull();
        assertThat(persisted.getReplacedBySessionId()).isNull();
        assertThat(rawTokenTextColumnOccurrences(issued.refreshToken())).isZero();
    }

    @Test
    void optionalDeviceLabelPersistsAsNullWithoutChangingSessionInvariants() throws NoSuchAlgorithmException {
        var userId = UUID.fromString("40000000-0000-0000-0000-000000000004");
        var authIdentityId = UUID.fromString("50000000-0000-0000-0000-000000000005");
        var sessionId = UUID.fromString("60000000-0000-0000-0000-000000000006");
        idGenerator.setNextIds(userId, authIdentityId, sessionId);
        registrationService.register("optional-label@example.com", RAW_PASSWORD);

        var issued = issuanceService.issue(userId, null);

        var persisted = deviceSessionRepository.findById(sessionId).orElseThrow();
        assertThat(persisted.getDeviceLabel()).isNull();
        assertThat(persisted.getFamilyId()).isEqualTo(sessionId);
        assertThat(persisted.getRefreshTokenHash()).isEqualTo(independentHash(issued.refreshToken()));
        assertThat(persisted.getCreatedAt()).isEqualTo(ISSUED_AT);
        assertThat(persisted.getExpiresAt()).isEqualTo(ISSUED_AT.plus(SESSION_LIFETIME));
        assertThat(rawTokenTextColumnOccurrences(issued.refreshToken())).isZero();
    }

    @Test
    void multipleInitialSessionsUseIndependentTokenFamilies() throws NoSuchAlgorithmException {
        var userId = UUID.fromString("70000000-0000-0000-0000-000000000007");
        var authIdentityId = UUID.fromString("80000000-0000-0000-0000-000000000008");
        var firstSessionId = UUID.fromString("90000000-0000-0000-0000-000000000009");
        var secondSessionId = UUID.fromString("a0000000-0000-0000-0000-00000000000a");
        idGenerator.setNextIds(userId, authIdentityId, firstSessionId, secondSessionId);
        registrationService.register("multiple-devices@example.com", RAW_PASSWORD);

        var first = issuanceService.issue(userId, "first device");
        var second = issuanceService.issue(userId, "second device");

        assertThat(first.sessionId()).isNotEqualTo(second.sessionId());
        assertThat(first.refreshToken()).isNotEqualTo(second.refreshToken());
        var firstPersisted = deviceSessionRepository.findById(firstSessionId).orElseThrow();
        var secondPersisted = deviceSessionRepository.findById(secondSessionId).orElseThrow();
        assertThat(firstPersisted.getFamilyId()).isEqualTo(firstSessionId);
        assertThat(secondPersisted.getFamilyId()).isEqualTo(secondSessionId);
        assertThat(firstPersisted.getFamilyId()).isNotEqualTo(secondPersisted.getFamilyId());
        assertThat(firstPersisted.getRefreshTokenHash()).isEqualTo(independentHash(first.refreshToken()));
        assertThat(secondPersisted.getRefreshTokenHash()).isEqualTo(independentHash(second.refreshToken()));
        assertThat(firstPersisted.getRefreshTokenHash()).isNotEqualTo(secondPersisted.getRefreshTokenHash());
        assertThat(firstPersisted.getRevokedAt()).isNull();
        assertThat(secondPersisted.getRevokedAt()).isNull();
        assertThat(deviceSessionRepository.count()).isEqualTo(2);
    }

    @Test
    void missingAndDisabledAccountsFailClosedWithoutCreatingSessions() {
        var missingUserId = UUID.fromString("b0000000-0000-0000-0000-00000000000b");
        var disabledUserId = UUID.fromString("c0000000-0000-0000-0000-00000000000c");
        var disabledAuthIdentityId = UUID.fromString("d0000000-0000-0000-0000-00000000000d");

        var missingFailure = catchThrowable(() -> issuanceService.issue(missingUserId, "missing device"));

        idGenerator.setNextIds(disabledUserId, disabledAuthIdentityId);
        registrationService.register("disabled-session@example.com", RAW_PASSWORD);
        runInTransaction(() -> jdbcTemplate.update(
                "UPDATE identity.user_account SET disabled_at = ? WHERE id = ?",
                DISABLED_AT.atOffset(ZoneOffset.UTC),
                disabledUserId));

        var disabledFailure = catchThrowable(() -> issuanceService.issue(disabledUserId, "disabled device"));

        assertCredentialFailure(missingFailure, missingUserId, "missing device");
        assertCredentialFailure(disabledFailure, disabledUserId, "disabled device");
        assertThat(deviceSessionRepository.count()).isZero();
    }

    private void assertCredentialFailure(Throwable thrown, UUID userId, String deviceLabel) {
        assertThat(thrown).isExactlyInstanceOf(AppException.class);
        var exception = (AppException) thrown;
        assertThat(exception.getErrorCode()).isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS);
        assertThat(exception.getParams()).isEmpty();
        assertThat(exception.getMessage()).isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS.getDescription());
        assertThat(exception.toString()).doesNotContain(userId.toString(), deviceLabel, "SHA-256");
    }

    private String independentHash(String rawToken) throws NoSuchAlgorithmException {
        var digest = MessageDigest.getInstance("SHA-256");
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
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

    private void runInTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOverrides {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(ISSUED_AT, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        RecordingIdGenerator recordingIdGenerator() {
            return new RecordingIdGenerator();
        }
    }
}
