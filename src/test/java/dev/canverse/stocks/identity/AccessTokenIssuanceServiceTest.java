package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.nimbusds.jose.util.JSONObjectUtils;
import dev.canverse.stocks.identity.application.AccessTokenIssuanceService;
import dev.canverse.stocks.identity.application.LocalAccountRegistrationService;
import dev.canverse.stocks.identity.application.RefreshSessionIssuanceService;
import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.identity.infrastructure.DeviceSessionRepository;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.testing.RecordingIdGenerator;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
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
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"stocks.identity.refresh-session.lifetime=2h", "stocks.identity.access-token.issuer=https://issuer.test",
                "stocks.identity.access-token.audience=canverse-test-api", "stocks.identity.access-token.lifetime=5m",
                "stocks.identity.access-token.key-id=test-ephemeral"})
@Testcontainers
@Import(AccessTokenIssuanceServiceTest.TestOverrides.class)
class AccessTokenIssuanceServiceTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-08-09T10:00:00.750Z");
    private static final Instant ISSUED_AT = OBSERVED_AT.truncatedTo(ChronoUnit.SECONDS);
    private static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(5);
    private static final Instant DISABLED_AT = Instant.parse("2026-08-09T09:30:00Z");
    private static final String RAW_PASSWORD = "correct horse battery staple";
    private static final String ISSUER = "https://issuer.test";
    private static final String AUDIENCE = "canverse-test-api";
    private static final String KEY_ID = "test-ephemeral";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    LocalAccountRegistrationService registrationService;

    @Autowired
    RefreshSessionIssuanceService refreshSessionIssuanceService;

    @Autowired
    AccessTokenIssuanceService accessTokenIssuanceService;

    @Autowired
    DeviceSessionRepository deviceSessionRepository;

    @Autowired
    KeyPair localAccessTokenKeyPair;

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
    void activeSessionReceivesOneExactVerifiedAccessTokenWithoutWrites() throws Exception {
        var userId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        var authIdentityId = UUID.fromString("20000000-0000-0000-0000-000000000002");
        var sessionId = UUID.fromString("30000000-0000-0000-0000-000000000003");
        var tokenId = UUID.fromString("40000000-0000-0000-0000-000000000004");
        idGenerator.setNextIds(userId, authIdentityId, sessionId, tokenId);
        registerAndIssueSession(userId, sessionId, "active@example.com");
        var beforeIssuance = snapshot();

        var issued = accessTokenIssuanceService.issue(sessionId);

        var decoded = decode(issued.accessToken(), (RSAPublicKey) localAccessTokenKeyPair.getPublic());
        var fullPrecisionExpiresAt = OBSERVED_AT.plus(ACCESS_TOKEN_LIFETIME);
        var expectedExpiresAt = fullPrecisionExpiresAt.truncatedTo(ChronoUnit.SECONDS);
        assertExactToken(issued.accessToken(), decoded, userId, sessionId, tokenId, expectedExpiresAt);
        assertThat(issued.expiresAt()).isEqualTo(expectedExpiresAt).isEqualTo(decoded.getExpiresAt());
        assertThat(issued.expiresAt()).isBefore(fullPrecisionExpiresAt);
        assertThat(snapshot()).isEqualTo(beforeIssuance);
        assertThat(persistedTokenMaterialOccurrences(issued.accessToken(), tokenId)).isZero();

        var unrelatedKeyPair = newRsaKeyPair();
        assertThatThrownBy(() -> decode(issued.accessToken(), (RSAPublicKey) unrelatedKeyPair.getPublic())).isInstanceOf(JwtException.class);
    }

    @Test
    void eachIssuanceUsesANewTokenInstanceIdWithoutWrites() {
        var userId = UUID.fromString("50000000-0000-0000-0000-000000000005");
        var authIdentityId = UUID.fromString("60000000-0000-0000-0000-000000000006");
        var sessionId = UUID.fromString("70000000-0000-0000-0000-000000000007");
        var firstTokenId = UUID.fromString("80000000-0000-0000-0000-000000000008");
        var secondTokenId = UUID.fromString("90000000-0000-0000-0000-000000000009");
        idGenerator.setNextIds(userId, authIdentityId, sessionId, firstTokenId, secondTokenId);
        registerAndIssueSession(userId, sessionId, "repeat@example.com");
        var beforeIssuance = snapshot();

        var first = accessTokenIssuanceService.issue(sessionId);
        var second = accessTokenIssuanceService.issue(sessionId);

        var firstDecoded = decode(first.accessToken(), (RSAPublicKey) localAccessTokenKeyPair.getPublic());
        var secondDecoded = decode(second.accessToken(), (RSAPublicKey) localAccessTokenKeyPair.getPublic());
        assertThat(firstDecoded.getId()).isEqualTo(firstTokenId.toString());
        assertThat(secondDecoded.getId()).isEqualTo(secondTokenId.toString());
        assertThat(firstDecoded.getId()).isNotEqualTo(secondDecoded.getId());
        assertThat(first.accessToken()).isNotEqualTo(second.accessToken());
        assertThat(firstDecoded.getSubject()).isEqualTo(secondDecoded.getSubject()).isEqualTo(userId.toString());
        assertThat(firstDecoded.getClaimAsString("sid")).isEqualTo(secondDecoded.getClaimAsString("sid")).isEqualTo(sessionId.toString());
        assertThat(firstDecoded.getIssuedAt()).isEqualTo(secondDecoded.getIssuedAt()).isEqualTo(ISSUED_AT);
        assertThat(firstDecoded.getExpiresAt()).isEqualTo(secondDecoded.getExpiresAt())
                .isEqualTo(OBSERVED_AT.plus(ACCESS_TOKEN_LIFETIME).truncatedTo(ChronoUnit.SECONDS));
        assertThat(snapshot()).isEqualTo(beforeIssuance);
    }

    @Test
    void accessExpiryIsCappedByRefreshSessionExpiryWithoutWrites() {
        var userId = UUID.fromString("a0000000-0000-0000-0000-00000000000a");
        var authIdentityId = UUID.fromString("b0000000-0000-0000-0000-00000000000b");
        var sessionId = UUID.fromString("c0000000-0000-0000-0000-00000000000c");
        var tokenId = UUID.fromString("d0000000-0000-0000-0000-00000000000d");
        idGenerator.setNextIds(userId, authIdentityId, sessionId, tokenId);
        registerAndIssueSession(userId, sessionId, "short-session@example.com");
        var sessionExpiresAt = OBSERVED_AT.plusSeconds(90).plusMillis(125);
        runInTransaction(() -> jdbcTemplate.update("UPDATE identity.device_session SET expires_at = ? WHERE id = ?", sessionExpiresAt.atOffset(ZoneOffset.UTC),
                sessionId));
        var beforeIssuance = snapshot();

        var issued = accessTokenIssuanceService.issue(sessionId);

        var decoded = decode(issued.accessToken(), (RSAPublicKey) localAccessTokenKeyPair.getPublic());
        var expectedExpiresAt = sessionExpiresAt.truncatedTo(ChronoUnit.SECONDS);
        assertThat(issued.expiresAt()).isEqualTo(expectedExpiresAt).isBefore(sessionExpiresAt);
        assertThat(decoded.getExpiresAt()).isEqualTo(expectedExpiresAt);
        assertThat(decoded.getId()).isEqualTo(tokenId.toString());
        assertThat(snapshot()).isEqualTo(beforeIssuance);
    }

    @Test
    void ineligibleSessionsFailClosedBeforeTokenIdGenerationWithoutWrites() {
        var missingSessionId = UUID.fromString("01000000-0000-0000-0000-000000000001");
        var revokedUserId = UUID.fromString("02000000-0000-0000-0000-000000000002");
        var revokedAuthId = UUID.fromString("03000000-0000-0000-0000-000000000003");
        var revokedSessionId = UUID.fromString("04000000-0000-0000-0000-000000000004");
        var expiredUserId = UUID.fromString("05000000-0000-0000-0000-000000000005");
        var expiredAuthId = UUID.fromString("06000000-0000-0000-0000-000000000006");
        var expiredSessionId = UUID.fromString("07000000-0000-0000-0000-000000000007");
        var disabledUserId = UUID.fromString("08000000-0000-0000-0000-000000000008");
        var disabledAuthId = UUID.fromString("09000000-0000-0000-0000-000000000009");
        var disabledSessionId = UUID.fromString("0a000000-0000-0000-0000-00000000000a");
        var nearExpiryUserId = UUID.fromString("0b000000-0000-0000-0000-00000000000b");
        var nearExpiryAuthId = UUID.fromString("0c000000-0000-0000-0000-00000000000c");
        var nearExpirySessionId = UUID.fromString("0d000000-0000-0000-0000-00000000000d");
        var unusedTokenId = UUID.fromString("0e000000-0000-0000-0000-00000000000e");
        idGenerator.setNextIds(revokedUserId, revokedAuthId, revokedSessionId, expiredUserId, expiredAuthId, expiredSessionId, disabledUserId, disabledAuthId,
                disabledSessionId, nearExpiryUserId, nearExpiryAuthId, nearExpirySessionId);
        registerAndIssueSession(revokedUserId, revokedSessionId, "revoked@example.com");
        registerAndIssueSession(expiredUserId, expiredSessionId, "expired@example.com");
        registerAndIssueSession(disabledUserId, disabledSessionId, "disabled@example.com");
        registerAndIssueSession(nearExpiryUserId, nearExpirySessionId, "near-expiry@example.com");
        runInTransaction(() -> {
            jdbcTemplate.update("UPDATE identity.device_session SET revoked_at = ?, revoke_reason = ? WHERE id = ?", OBSERVED_AT.atOffset(ZoneOffset.UTC),
                    "test fixture", revokedSessionId);
            jdbcTemplate.update("UPDATE identity.device_session SET created_at = ?, expires_at = ? WHERE id = ?",
                    OBSERVED_AT.minus(Duration.ofHours(1)).atOffset(ZoneOffset.UTC), OBSERVED_AT.atOffset(ZoneOffset.UTC), expiredSessionId);
            jdbcTemplate.update("UPDATE identity.user_account SET disabled_at = ? WHERE id = ?", DISABLED_AT.atOffset(ZoneOffset.UTC), disabledUserId);
            jdbcTemplate.update("UPDATE identity.device_session SET expires_at = ? WHERE id = ?", OBSERVED_AT.plusMillis(100).atOffset(ZoneOffset.UTC),
                    nearExpirySessionId);
        });
        idGenerator.setNextIds(unusedTokenId);
        var beforeIssuance = snapshot();

        var missingFailure = catchThrowable(() -> accessTokenIssuanceService.issue(missingSessionId));
        var revokedFailure = catchThrowable(() -> accessTokenIssuanceService.issue(revokedSessionId));
        var expiredFailure = catchThrowable(() -> accessTokenIssuanceService.issue(expiredSessionId));
        var disabledFailure = catchThrowable(() -> accessTokenIssuanceService.issue(disabledSessionId));
        var nearExpiryFailure = catchThrowable(() -> accessTokenIssuanceService.issue(nearExpirySessionId));

        assertCredentialFailure(missingFailure, missingSessionId);
        assertCredentialFailure(revokedFailure, revokedSessionId);
        assertCredentialFailure(expiredFailure, expiredSessionId);
        assertCredentialFailure(disabledFailure, disabledSessionId);
        assertCredentialFailure(nearExpiryFailure, nearExpirySessionId);
        assertThat(idGenerator.remainingIds()).isOne();
        assertThat(idGenerator.peekNextId()).isEqualTo(unusedTokenId);
        assertThat(snapshot()).isEqualTo(beforeIssuance);
    }

    private void registerAndIssueSession(UUID userId, UUID sessionId, String email) {
        assertThat(registrationService.register(email, RAW_PASSWORD)).isEqualTo(userId);
        assertThat(refreshSessionIssuanceService.issue(userId, "test device").sessionId()).isEqualTo(sessionId);
    }

    private void assertExactToken(String compactToken, Jwt decoded, UUID userId, UUID sessionId, UUID tokenId, Instant expectedExpiresAt) throws Exception {
        var parts = compactToken.split("\\.");
        assertThat(parts).hasSize(3);
        Map<String, Object> headers = decodeRawJsonPart(parts[0]);
        Map<String, Object> claims = decodeRawJsonPart(parts[1]);

        assertThat(headers).containsOnlyKeys("alg", "kid", "typ").containsEntry("alg", "RS256").containsEntry("kid", KEY_ID).containsEntry("typ", "access");
        assertThat(claims).containsOnlyKeys("iss", "sub", "aud", "iat", "nbf", "exp", "jti", "sid");
        assertThat(claims.get("iss")).isEqualTo(ISSUER);
        assertThat(claims.get("sub")).isEqualTo(userId.toString());
        assertThat(claims.get("aud")).isInstanceOf(String.class).isEqualTo(AUDIENCE);
        assertThat(claims.get("iat")).isInstanceOf(Number.class);
        assertThat(((Number) claims.get("iat")).longValue()).isEqualTo(ISSUED_AT.getEpochSecond());
        assertThat(claims.get("nbf")).isInstanceOf(Number.class);
        assertThat(((Number) claims.get("nbf")).longValue()).isEqualTo(ISSUED_AT.getEpochSecond());
        assertThat(claims.get("exp")).isInstanceOf(Number.class);
        assertThat(((Number) claims.get("exp")).longValue()).isEqualTo(expectedExpiresAt.getEpochSecond());
        assertThat(claims.get("jti")).isEqualTo(tokenId.toString());
        assertThat(claims.get("sid")).isEqualTo(sessionId.toString());

        assertThat(decoded.getIssuer()).isEqualTo(URI.create(ISSUER).toURL());
        assertThat(decoded.getSubject()).isEqualTo(userId.toString());
        assertThat(decoded.getAudience()).containsExactly(AUDIENCE);
        assertThat(decoded.getIssuedAt()).isEqualTo(ISSUED_AT);
        assertThat(decoded.getNotBefore()).isEqualTo(ISSUED_AT);
        assertThat(decoded.getExpiresAt()).isEqualTo(expectedExpiresAt);
        assertThat(decoded.getId()).isEqualTo(tokenId.toString());
        assertThat(decoded.getClaimAsString("sid")).isEqualTo(sessionId.toString());
    }

    private Map<String, Object> decodeRawJsonPart(String encodedPart) throws Exception {
        var rawJson = new String(Base64.getUrlDecoder().decode(encodedPart), StandardCharsets.UTF_8);
        return JSONObjectUtils.parse(rawJson);
    }

    private Jwt decode(String compactToken, RSAPublicKey publicKey) {
        var decoder = NimbusJwtDecoder.withPublicKey(publicKey).signatureAlgorithm(SignatureAlgorithm.RS256).validateType(false).build();
        decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
        return decoder.decode(compactToken);
    }

    private KeyPair newRsaKeyPair() throws NoSuchAlgorithmException {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private void assertCredentialFailure(Throwable thrown, UUID sessionId) {
        assertThat(thrown).isExactlyInstanceOf(AppException.class);
        var exception = (AppException) thrown;
        assertThat(exception.getErrorCode()).isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS);
        assertThat(exception.getParams()).isEmpty();
        assertThat(exception.getMessage()).isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS.getDescription());
        assertThat(exception.toString()).doesNotContain(sessionId.toString(), KEY_ID, ISSUER, AUDIENCE);
    }

    private long persistedTokenMaterialOccurrences(String compactToken, UUID tokenId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM identity.device_session" + " WHERE strpos(refresh_token_hash, ?) > 0" + " OR strpos(coalesce(device_label, ''), ?) > 0" +
                        " OR strpos(coalesce(revoke_reason, ''), ?) > 0" + " OR strpos(refresh_token_hash, ?) > 0" +
                        " OR strpos(coalesce(device_label, ''), ?) > 0" + " OR strpos(coalesce(revoke_reason, ''), ?) > 0",
                Long.class, compactToken, compactToken, compactToken, tokenId.toString(), tokenId.toString(), tokenId.toString());
    }

    private PersistedIdentityState snapshot() {
        return new PersistedIdentityState(List.copyOf(jdbcTemplate.queryForList("SELECT * FROM identity.user_account ORDER BY id")),
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

        @Bean
        @Primary
        RecordingIdGenerator recordingIdGenerator() {
            return new RecordingIdGenerator();
        }
    }

    private record PersistedIdentityState(List<Map<String, Object>> users, List<Map<String, Object>> identities, List<Map<String, Object>> sessions) {}
}
