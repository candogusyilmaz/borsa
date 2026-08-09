package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import dev.canverse.stocks.identity.application.AccessTokenIssuanceService;
import dev.canverse.stocks.identity.application.LocalAccessTokenAuthenticationConverter;
import dev.canverse.stocks.identity.application.LocalAccountRegistrationService;
import dev.canverse.stocks.identity.application.RefreshSessionIssuanceService;
import dev.canverse.stocks.identity.infrastructure.DeviceSessionRepository;
import dev.canverse.stocks.platform.id.IdGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        properties = {
            "stocks.identity.refresh-session.lifetime=2h",
            "stocks.identity.access-token.issuer=https://issuer.test",
            "stocks.identity.access-token.audience=canverse-test-api",
            "stocks.identity.access-token.lifetime=5m",
            "stocks.identity.access-token.key-id=test-ephemeral"
        })
@Testcontainers
@Import(LocalAccessTokenAuthenticationConverterTest.TestOverrides.class)
class LocalAccessTokenAuthenticationConverterTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-08-09T16:00:00.750Z");
    private static final Instant ISSUED_AT = OBSERVED_AT.truncatedTo(ChronoUnit.SECONDS);
    private static final String RAW_PASSWORD = "correct horse battery staple";
    private static final String SAFE_MESSAGE = "The bearer token is invalid.";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    LocalAccountRegistrationService registrationService;

    @Autowired
    RefreshSessionIssuanceService refreshSessionIssuanceService;

    @Autowired
    AccessTokenIssuanceService accessTokenIssuanceService;

    @Autowired
    LocalAccessTokenAuthenticationConverter converter;

    @Autowired
    DeviceSessionRepository deviceSessionRepository;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    JwtDecoder jwtDecoder;

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
    void validCurrentSessionBecomesMinimalAuthenticatedJwtIdentityWithoutWrites() {
        var fixture = registerAndIssueAccess(
                uuid("10000000-0000-4000-8000-000000000001"),
                uuid("20000000-0000-4000-8000-000000000002"),
                uuid("30000000-0000-4000-8000-000000000003"),
                uuid("40000000-0000-4000-8000-000000000004"),
                "valid-access@example.com");
        var beforeConversion = snapshot();

        var converted = converter.convert(fixture.jwt());

        assertThat(converted).isExactlyInstanceOf(JwtAuthenticationToken.class);
        var authentication = (JwtAuthenticationToken) converted;
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getToken()).isSameAs(fixture.jwt());
        assertThat(authentication.getPrincipal()).isSameAs(fixture.jwt());
        assertThat(authentication.getName()).isEqualTo(fixture.userAccountId().toString());
        assertThat(authentication.getAuthorities()).isEmpty();
        assertThat(authentication.getToken().getClaimAsString("sid"))
                .isEqualTo(fixture.sessionId().toString());
        assertThat(snapshot()).isEqualTo(beforeConversion);
    }

    @Test
    void missingCrossUserRevokedExpiredAndDisabledStateFailsUniformlyWithoutWrites() {
        var revoked = registerAndIssueAccess(
                uuid("11000000-0000-4000-8000-000000000011"),
                uuid("12000000-0000-4000-8000-000000000012"),
                uuid("13000000-0000-4000-8000-000000000013"),
                uuid("14000000-0000-4000-8000-000000000014"),
                "revoked-access@example.com");
        var expired = registerAndIssueAccess(
                uuid("21000000-0000-4000-8000-000000000021"),
                uuid("22000000-0000-4000-8000-000000000022"),
                uuid("23000000-0000-4000-8000-000000000023"),
                uuid("24000000-0000-4000-8000-000000000024"),
                "expired-access@example.com");
        var disabled = registerAndIssueAccess(
                uuid("31000000-0000-4000-8000-000000000031"),
                uuid("32000000-0000-4000-8000-000000000032"),
                uuid("33000000-0000-4000-8000-000000000033"),
                uuid("34000000-0000-4000-8000-000000000034"),
                "disabled-access@example.com");
        var crossUserSession = registerAndIssueAccess(
                uuid("41000000-0000-4000-8000-000000000041"),
                uuid("42000000-0000-4000-8000-000000000042"),
                uuid("43000000-0000-4000-8000-000000000043"),
                uuid("44000000-0000-4000-8000-000000000044"),
                "session-owner@example.com");
        var crossUserSubject = registerAndIssueAccess(
                uuid("51000000-0000-4000-8000-000000000051"),
                uuid("52000000-0000-4000-8000-000000000052"),
                uuid("53000000-0000-4000-8000-000000000053"),
                uuid("54000000-0000-4000-8000-000000000054"),
                "different-subject@example.com");
        var missingSessionId = uuid("61000000-0000-4000-8000-000000000061");
        var missingJwt =
                signAndDecode(revoked.userAccountId(), missingSessionId, uuid("62000000-0000-4000-8000-000000000062"));
        var crossUserJwt = signAndDecode(
                crossUserSubject.userAccountId(),
                crossUserSession.sessionId(),
                uuid("63000000-0000-4000-8000-000000000063"));
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
                    OBSERVED_AT.minus(Duration.ofMinutes(30)).atOffset(ZoneOffset.UTC),
                    disabled.userAccountId());
        });
        var beforeConversion = snapshot();

        var missingFailure = convertRejected(missingJwt, beforeConversion);
        var crossUserFailure = convertRejected(crossUserJwt, beforeConversion);
        var revokedFailure = convertRejected(revoked.jwt(), beforeConversion);
        var expiredFailure = convertRejected(expired.jwt(), beforeConversion);
        var disabledFailure = convertRejected(disabled.jwt(), beforeConversion);

        var failures = List.of(missingFailure, crossUserFailure, revokedFailure, expiredFailure, disabledFailure);
        assertThat(failures).extracting(Throwable::getMessage).containsOnly(SAFE_MESSAGE);
        var sensitiveValues = List.of(
                missingJwt.getTokenValue(),
                crossUserJwt.getTokenValue(),
                revoked.jwt().getTokenValue(),
                expired.jwt().getTokenValue(),
                disabled.jwt().getTokenValue(),
                missingSessionId.toString(),
                revoked.userAccountId().toString(),
                revoked.sessionId().toString(),
                expired.userAccountId().toString(),
                expired.sessionId().toString(),
                disabled.userAccountId().toString(),
                disabled.sessionId().toString(),
                crossUserSubject.userAccountId().toString(),
                crossUserSession.sessionId().toString(),
                "test fixture revocation",
                "revoked",
                "expired",
                "disabled",
                "cross-user");
        failures.forEach(failure -> assertThat(failure.toString()).doesNotContain(sensitiveValues));
    }

    @Test
    void ownerScopedRepositoryMethodResolvesOnlyTheExactSessionUserPairWithoutWrites() {
        var owner = registerAndIssueAccess(
                uuid("71000000-0000-4000-8000-000000000071"),
                uuid("72000000-0000-4000-8000-000000000072"),
                uuid("73000000-0000-4000-8000-000000000073"),
                uuid("74000000-0000-4000-8000-000000000074"),
                "repository-owner@example.com");
        var other = registerAndIssueAccess(
                uuid("81000000-0000-4000-8000-000000000081"),
                uuid("82000000-0000-4000-8000-000000000082"),
                uuid("83000000-0000-4000-8000-000000000083"),
                uuid("84000000-0000-4000-8000-000000000084"),
                "repository-other@example.com");
        var beforeLookup = snapshot();

        var exactMatch = deviceSessionRepository.findByIdAndUserAccount_Id(owner.sessionId(), owner.userAccountId());
        var crossUserMatch =
                deviceSessionRepository.findByIdAndUserAccount_Id(owner.sessionId(), other.userAccountId());

        assertThat(exactMatch).get().extracting(session -> session.getId()).isEqualTo(owner.sessionId());
        assertThat(crossUserMatch).isEmpty();
        assertThat(snapshot()).isEqualTo(beforeLookup);
    }

    @Test
    void resourceServerLibraryAndConverterAddNoHttpSecurityBoundary() {
        assertThat(applicationContext.getBeansOfType(LocalAccessTokenAuthenticationConverter.class))
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(SecurityFilterChain.class)).isEmpty();
    }

    private IssuedIdentity registerAndIssueAccess(
            UUID userAccountId, UUID authIdentityId, UUID sessionId, UUID tokenId, String email) {
        idGenerator.setNextIds(userAccountId, authIdentityId, sessionId, tokenId);
        assertThat(registrationService.register(email, RAW_PASSWORD)).isEqualTo(userAccountId);
        assertThat(refreshSessionIssuanceService
                        .issue(userAccountId, "test device")
                        .sessionId())
                .isEqualTo(sessionId);
        var issuedAccessToken = accessTokenIssuanceService.issue(sessionId);
        return new IssuedIdentity(userAccountId, sessionId, jwtDecoder.decode(issuedAccessToken.accessToken()));
    }

    private Jwt signAndDecode(UUID userAccountId, UUID sessionId, UUID tokenId) {
        var headers = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId("test-ephemeral")
                .type("access")
                .build();
        var claims = JwtClaimsSet.builder()
                .issuer("https://issuer.test")
                .subject(userAccountId.toString())
                .audience(List.of("canverse-test-api"))
                .issuedAt(ISSUED_AT)
                .notBefore(ISSUED_AT)
                .expiresAt(ISSUED_AT.plus(Duration.ofMinutes(5)))
                .id(tokenId.toString())
                .claim("sid", sessionId.toString())
                .build();
        var compactToken =
                jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
        return jwtDecoder.decode(compactToken);
    }

    private InvalidBearerTokenException convertRejected(Jwt jwt, PersistedIdentityState expectedState) {
        var thrown = catchThrowable(() -> converter.convert(jwt));

        assertThat(thrown).isExactlyInstanceOf(InvalidBearerTokenException.class);
        var exception = (InvalidBearerTokenException) thrown;
        assertThat(exception.getError().getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_TOKEN);
        assertThat(exception.getMessage()).isEqualTo(SAFE_MESSAGE);
        assertThat(snapshot()).isEqualTo(expectedState);
        return exception;
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

    private UUID uuid(String value) {
        return UUID.fromString(value);
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

    static final class RecordingIdGenerator implements IdGenerator {

        private final Deque<UUID> nextIds = new ArrayDeque<>();

        void setNextIds(UUID... ids) {
            nextIds.clear();
            nextIds.addAll(Arrays.asList(ids));
        }

        @Override
        public UUID next() {
            return nextIds.removeFirst();
        }
    }

    private record IssuedIdentity(UUID userAccountId, UUID sessionId, Jwt jwt) {}

    private record PersistedIdentityState(
            List<Map<String, Object>> users,
            List<Map<String, Object>> identities,
            List<Map<String, Object>> sessions) {}
}
