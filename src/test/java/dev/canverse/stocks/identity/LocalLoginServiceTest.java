package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import dev.canverse.stocks.identity.application.LocalAccountRegistrationService;
import dev.canverse.stocks.identity.application.LocalLoginService;
import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.identity.infrastructure.DeviceSessionRepository;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.testing.RecordingIdGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtEncodingException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"stocks.identity.refresh-session.lifetime=2h", "stocks.identity.access-token.lifetime=5m"})
@Testcontainers
@Import(LocalLoginServiceTest.TestOverrides.class)
class LocalLoginServiceTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-08-09T11:00:00.750Z");
    private static final Duration REFRESH_SESSION_LIFETIME = Duration.ofHours(2);
    private static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(5);
    private static final String RAW_PASSWORD = "correct horse battery staple";
    private static final String WRONG_PASSWORD = "incorrect horse battery staple";
    private static final String ACCESS_TOKEN = "deterministic-access-token";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    LocalAccountRegistrationService registrationService;

    @Autowired
    LocalLoginService loginService;

    @Autowired
    DeviceSessionRepository deviceSessionRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    RecordingIdGenerator idGenerator;

    @Autowired
    ControllableJwtEncoder jwtEncoder;

    @BeforeEach
    void clearIdentityTables() {
        runInTransaction(() -> {
            jdbcTemplate.update("DELETE FROM identity.device_session");
            jdbcTemplate.update("DELETE FROM identity.auth_identity");
            jdbcTemplate.update("DELETE FROM identity.user_account");
        });
        idGenerator.setNextIds();
        jwtEncoder.reset();
    }

    @Test
    void successfulLocalLoginCommitsOneExactComposedResult() throws NoSuchAlgorithmException {
        var userId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        var authIdentityId = UUID.fromString("20000000-0000-0000-0000-000000000002");
        var sessionId = UUID.fromString("30000000-0000-0000-0000-000000000003");
        var tokenId = UUID.fromString("40000000-0000-0000-0000-000000000004");
        var deviceLabel = "Alice's laptop";
        idGenerator.setNextIds(userId, authIdentityId);
        registrationService.register("Alice.Login@Example.COM", RAW_PASSWORD);
        var identityBeforeLogin = identitySnapshot();
        idGenerator.setNextIds(sessionId, tokenId);

        var result = loginService.login("ALICE.LOGIN@EXAMPLE.COM", RAW_PASSWORD, deviceLabel);

        var persistedSession = persistedSession(sessionId);
        var expectedRefreshExpiry = OBSERVED_AT.plus(REFRESH_SESSION_LIFETIME);
        var expectedAccessExpiry = OBSERVED_AT.plus(ACCESS_TOKEN_LIFETIME).truncatedTo(ChronoUnit.SECONDS);
        assertThat(result.sessionId()).isEqualTo(sessionId).isEqualTo(persistedSession.id());
        assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(result.accessTokenExpiresAt())
                .isEqualTo(expectedAccessExpiry)
                .isEqualTo(jwtEncoder.lastParameters().getClaims().getExpiresAt());
        assertThat(result.refreshTokenExpiresAt())
                .isEqualTo(expectedRefreshExpiry)
                .isEqualTo(persistedSession.expiresAt());
        assertThat(persistedSession.userAccountId()).isEqualTo(userId);
        assertThat(persistedSession.familyId()).isEqualTo(sessionId);
        assertThat(persistedSession.deviceLabel()).isEqualTo(deviceLabel);
        assertThat(persistedSession.createdAt()).isEqualTo(OBSERVED_AT);
        assertThat(persistedSession.lastUsedAt()).isNull();
        assertThat(persistedSession.revokedAt()).isNull();
        assertThat(persistedSession.revokeReason()).isNull();
        assertThat(persistedSession.replacedBySessionId()).isNull();
        assertThat(persistedSession.refreshTokenHash()).isEqualTo(independentHash(result.refreshToken()));
        assertThat(persistedSession.refreshTokenHash()).isNotEqualTo(result.refreshToken());
        assertThat(rawTokenTextColumnOccurrences(result.refreshToken())).isZero();
        assertThat(jwtEncoder.invocations()).isOne();
        assertThat(jwtEncoder.sessionRowsVisibleAtEncode()).isOne();
        assertThat(jwtEncoder.lastParameters().getClaims().getClaimAsString("sid"))
                .isEqualTo(sessionId.toString());
        assertThat(jwtEncoder.lastParameters().getClaims().getId()).isEqualTo(tokenId.toString());
        assertThat(idGenerator.invocations()).isGreaterThanOrEqualTo(2);
        assertThat(deviceSessionRepository.count()).isOne();
        assertThat(identitySnapshot()).isEqualTo(identityBeforeLogin);
    }

    @Test
    void invalidCredentialsShortCircuitBeforeSessionOrAccessTokenIssuance() {
        var userId = UUID.fromString("50000000-0000-0000-0000-000000000005");
        var authIdentityId = UUID.fromString("60000000-0000-0000-0000-000000000006");
        var unusedSessionId = UUID.fromString("70000000-0000-0000-0000-000000000007");
        var unusedTokenId = UUID.fromString("80000000-0000-0000-0000-000000000008");
        var email = "invalid-login@example.com";
        var deviceLabel = "unissued device";
        idGenerator.setNextIds(userId, authIdentityId);
        registrationService.register(email, RAW_PASSWORD);
        var identityBeforeLogin = identitySnapshot();
        idGenerator.setNextIds(unusedSessionId, unusedTokenId);

        var thrown = catchThrowable(() -> loginService.login(email, WRONG_PASSWORD, deviceLabel));

        assertCredentialFailure(thrown, email, WRONG_PASSWORD, deviceLabel);
        assertThat(deviceSessionRepository.count()).isZero();
        assertThat(jwtEncoder.invocations()).isZero();
        assertThat(idGenerator.invocations()).isZero();
        assertThat(idGenerator.remainingIds()).isEqualTo(2);
        assertThat(identitySnapshot()).isEqualTo(identityBeforeLogin);
    }

    @Test
    void successfulLocalLoginPassesThroughANullDeviceLabel() {
        var userId = UUID.fromString("81000000-0000-0000-0000-000000000001");
        var authIdentityId = UUID.fromString("82000000-0000-0000-0000-000000000002");
        var sessionId = UUID.fromString("83000000-0000-0000-0000-000000000003");
        var tokenId = UUID.fromString("84000000-0000-0000-0000-000000000004");
        idGenerator.setNextIds(userId, authIdentityId);
        registrationService.register("null-label-login@example.com", RAW_PASSWORD);
        idGenerator.setNextIds(sessionId, tokenId);

        var result = loginService.login("null-label-login@example.com", RAW_PASSWORD, null);

        assertThat(result.sessionId()).isEqualTo(sessionId);
        assertThat(persistedSession(sessionId).deviceLabel()).isNull();
        assertThat(jwtEncoder.lastParameters().getClaims().getClaimAsString("sid"))
                .isEqualTo(sessionId.toString());
        assertThat(deviceSessionRepository.count()).isOne();
    }

    @Test
    void accessTokenEncodingFailureRollsBackTheFlushedRefreshSession() {
        var userId = UUID.fromString("90000000-0000-0000-0000-000000000009");
        var authIdentityId = UUID.fromString("a0000000-0000-0000-0000-00000000000a");
        var sessionId = UUID.fromString("b0000000-0000-0000-0000-00000000000b");
        var tokenId = UUID.fromString("c0000000-0000-0000-0000-00000000000c");
        var email = "rollback-login@example.com";
        var deviceLabel = "rolled-back device";
        idGenerator.setNextIds(userId, authIdentityId);
        registrationService.register(email, RAW_PASSWORD);
        var identityBeforeLogin = identitySnapshot();
        idGenerator.setNextIds(sessionId, tokenId);
        var encodingFailure = new JwtEncodingException("forced test encoding failure");
        jwtEncoder.failWith(encodingFailure);

        var thrown = catchThrowable(() -> loginService.login(email, RAW_PASSWORD, deviceLabel));

        assertThat(thrown).isSameAs(encodingFailure);
        assertThat(thrown.toString())
                .doesNotContain(email, RAW_PASSWORD, deviceLabel, sessionId.toString(), tokenId.toString());
        assertThat(jwtEncoder.invocations()).isOne();
        assertThat(jwtEncoder.sessionRowsVisibleAtEncode()).isOne();
        assertThat(jwtEncoder.lastParameters().getClaims().getClaimAsString("sid"))
                .isEqualTo(sessionId.toString());
        assertThat(idGenerator.invocations()).isEqualTo(2);
        assertThat(deviceSessionRepository.count()).isZero();
        assertThat(identitySnapshot()).isEqualTo(identityBeforeLogin);
    }

    @Test
    void nullCredentialsFailBeforeIssuance() {
        assertThatThrownBy(() -> loginService.login(null, RAW_PASSWORD, "device"))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("email");
        assertThatThrownBy(() -> loginService.login("alice@example.com", null, "device"))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("rawPassword");

        assertThat(deviceSessionRepository.count()).isZero();
        assertThat(jwtEncoder.invocations()).isZero();
        assertThat(idGenerator.invocations()).isZero();
    }

    private void assertCredentialFailure(Throwable thrown, String email, String rawPassword, String deviceLabel) {
        assertThat(thrown).isExactlyInstanceOf(AppException.class);
        var exception = (AppException) thrown;
        assertThat(exception.getErrorCode()).isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS);
        assertThat(exception.getParams()).isEmpty();
        assertThat(exception.getMessage()).isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS.getDescription());
        assertThat(exception.toString()).doesNotContain(email, rawPassword, deviceLabel);
    }

    private PersistedSession persistedSession(UUID sessionId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            var session = deviceSessionRepository.findById(sessionId).orElseThrow();
            return new PersistedSession(
                    session.getId(),
                    session.getUserAccount().getId(),
                    session.getFamilyId(),
                    session.getRefreshTokenHash(),
                    session.getDeviceLabel(),
                    session.getCreatedAt(),
                    session.getLastUsedAt(),
                    session.getExpiresAt(),
                    session.getRevokedAt(),
                    session.getRevokeReason(),
                    session.getReplacedBySessionId());
        });
    }

    private IdentityState identitySnapshot() {
        return new IdentityState(
                List.copyOf(jdbcTemplate.queryForList("SELECT * FROM identity.user_account ORDER BY id")),
                List.copyOf(jdbcTemplate.queryForList("SELECT * FROM identity.auth_identity ORDER BY id")));
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
            return Clock.fixed(OBSERVED_AT, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        RecordingIdGenerator recordingIdGenerator() {
            return new RecordingIdGenerator();
        }

        @Bean
        @Primary
        ControllableJwtEncoder controllableJwtEncoder(JdbcTemplate jdbcTemplate) {
            return new ControllableJwtEncoder(jdbcTemplate);
        }
    }

    static final class ControllableJwtEncoder implements JwtEncoder {

        private final JdbcTemplate jdbcTemplate;
        private int invocations;
        private long sessionRowsVisibleAtEncode;
        private JwtEncoderParameters lastParameters;
        private RuntimeException failure;

        ControllableJwtEncoder(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        void reset() {
            invocations = 0;
            sessionRowsVisibleAtEncode = 0;
            lastParameters = null;
            failure = null;
        }

        void failWith(RuntimeException failure) {
            this.failure = failure;
        }

        int invocations() {
            return invocations;
        }

        long sessionRowsVisibleAtEncode() {
            return sessionRowsVisibleAtEncode;
        }

        JwtEncoderParameters lastParameters() {
            return lastParameters;
        }

        @Override
        public Jwt encode(JwtEncoderParameters parameters) {
            invocations++;
            lastParameters = parameters;
            sessionRowsVisibleAtEncode =
                    jdbcTemplate.queryForObject("SELECT count(*) FROM identity.device_session", Long.class);
            if (failure != null) {
                throw failure;
            }

            var claims = parameters.getClaims();
            return new Jwt(
                    ACCESS_TOKEN,
                    claims.getIssuedAt(),
                    claims.getExpiresAt(),
                    parameters.getJwsHeader().getHeaders(),
                    claims.getClaims());
        }
    }

    private record PersistedSession(
            UUID id,
            UUID userAccountId,
            UUID familyId,
            String refreshTokenHash,
            String deviceLabel,
            Instant createdAt,
            Instant lastUsedAt,
            Instant expiresAt,
            Instant revokedAt,
            String revokeReason,
            UUID replacedBySessionId) {}

    private record IdentityState(List<Map<String, Object>> users, List<Map<String, Object>> identities) {}
}
