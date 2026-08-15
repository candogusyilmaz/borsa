package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.canverse.stocks.identity.application.AccessTokenIssuanceService;
import dev.canverse.stocks.identity.application.LocalAccountRegistrationService;
import dev.canverse.stocks.identity.application.RefreshSessionIssuanceService;
import dev.canverse.stocks.identity.application.RefreshSessionRotationService;
import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.identity.infrastructure.DeviceSessionRepository;
import dev.canverse.stocks.identity.infrastructure.SecureRefreshTokenGenerator;
import dev.canverse.stocks.identity.infrastructure.UserAccountRepository;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.id.IdGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "stocks.identity.refresh-session.lifetime=2h",
            "stocks.identity.access-token.issuer=https://issuer.test",
            "stocks.identity.access-token.audience=canverse-test-api",
            "stocks.identity.access-token.lifetime=5m",
            "stocks.identity.access-token.key-id=test-ephemeral"
        })
@Testcontainers
@Import(RefreshSessionRotationServiceTest.TestOverrides.class)
class RefreshSessionRotationServiceTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-08-15T12:00:00.750Z");
    private static final Duration REFRESH_LIFETIME = Duration.ofHours(2);
    private static final String RAW_PASSWORD = "correct horse battery staple";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    LocalAccountRegistrationService registrationService;

    @Autowired
    RefreshSessionIssuanceService issuanceService;

    @Autowired
    dev.canverse.stocks.identity.application.AccessTokenIssuanceService accessTokenIssuanceService;

    @Autowired
    RefreshSessionRotationService rotationService;

    @Autowired
    dev.canverse.stocks.identity.application.RefreshSessionAuthenticationService refreshAuthenticationService;

    @Autowired
    dev.canverse.stocks.identity.application.LocalAccessTokenAuthenticationConverter accessTokenConverter;

    @Autowired
    JwtDecoder jwtDecoder;

    @Autowired
    SecureRefreshTokenGenerator refreshTokenGenerator;

    @Autowired
    DeviceSessionRepository deviceSessionRepository;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    RecordingIdGenerator idGenerator;

    @Autowired
    Clock clock;

    @BeforeEach
    void clearIdentityTables() {
        runInTransaction(() -> {
            jdbcTemplate.update("DELETE FROM identity.device_session");
            jdbcTemplate.update("DELETE FROM identity.auth_identity");
            jdbcTemplate.update("DELETE FROM identity.user_account");
        });
        idGenerator.reset();
    }

    @Test
    void normalRotationRetainsTwoGenerationHistoryAndBindsSuccessorAccessToken() {
        var fixture = registerAndIssue(
                uuid("10000000-0000-4000-8000-000000000001"),
                uuid("20000000-0000-4000-8000-000000000002"),
                uuid("30000000-0000-4000-8000-000000000003"),
                uuid("40000000-0000-4000-8000-000000000004"),
                "normal@example.com");
        var replacementId = uuid("50000000-0000-4000-8000-000000000005");
        var replacementAccessTokenId = uuid("60000000-0000-4000-8000-000000000006");
        idGenerator.setNextIds(replacementId, replacementAccessTokenId);

        var rotated = rotationService.rotate(fixture.refreshToken()).orElseThrow();

        assertThat(rotated.sessionId()).isEqualTo(replacementId);
        assertThat(rotated.refreshTokenExpiresAt()).isEqualTo(OBSERVED_AT.plus(REFRESH_LIFETIME));
        assertThat(refreshAuthenticationService.authenticate(rotated.refreshToken()))
                .isEqualTo(replacementId);
        assertThatThrownBy(() -> refreshAuthenticationService.authenticate(fixture.refreshToken()))
                .isExactlyInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS);

        var oldState = persistedSession(fixture.sessionId());
        var newState = persistedSession(replacementId);
        assertThat(oldState.lastUsedAt()).isEqualTo(OBSERVED_AT);
        assertThat(oldState.revokedAt()).isEqualTo(OBSERVED_AT);
        assertThat(oldState.revokeReason()).isEqualTo("ROTATED");
        assertThat(oldState.replacedBySessionId()).isEqualTo(replacementId);
        assertThat(newState.userAccountId()).isEqualTo(fixture.userId());
        assertThat(newState.familyId()).isEqualTo(fixture.sessionId());
        assertThat(newState.deviceLabel()).isEqualTo("phone");
        assertThat(newState.createdAt()).isEqualTo(OBSERVED_AT);
        assertThat(newState.expiresAt()).isEqualTo(oldState.expiresAt());
        assertThat(newState.lastUsedAt()).isNull();
        assertThat(newState.revokedAt()).isNull();
        assertThat(newState.revokeReason()).isNull();
        assertThat(newState.replacedBySessionId()).isNull();
        assertThat(newState.refreshTokenHash()).isEqualTo(refreshTokenGenerator.hash(rotated.refreshToken()));
        assertThat(deviceSessionRepository.findByFamilyIdAndRevokedAtIsNull(fixture.sessionId()))
                .hasValueSatisfying(session -> assertThat(session.getId()).isEqualTo(replacementId));
        assertThat(jwtDecoder.decode(rotated.accessToken()).getClaimAsString("sid"))
                .isEqualTo(replacementId.toString());
        assertThatThrownBy(() -> accessTokenConverter.convert(jwtDecoder.decode(fixture.accessToken())))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void invalidPostgresStatesDoNotMutatePersistenceOrAllocateCredentials() {
        var unknownBefore = persistedDatabaseState();
        idGenerator.reset();
        assertThat(rotationService.rotate("unknown-token")).isEmpty();
        assertThat(persistedDatabaseState()).isEqualTo(unknownBefore);
        assertThat(idGenerator.consumedIds()).isEmpty();

        var expired = registerAndIssue(
                uuid("70000000-0000-4000-8000-000000000007"),
                uuid("71000000-0000-4000-8000-000000000017"),
                uuid("72000000-0000-4000-8000-000000000027"),
                uuid("73000000-0000-4000-8000-000000000037"),
                "expired@example.com");
        runInTransaction(() -> {
            var updated = jdbcTemplate.update(
                    "UPDATE identity.device_session SET created_at = "
                            + "TIMESTAMPTZ '2026-08-15 11:59:59.750+00', "
                            + "expires_at = TIMESTAMPTZ '2026-08-15 12:00:00.750+00' WHERE refresh_token_hash = ?",
                    refreshTokenGenerator.hash(expired.refreshToken()));
            assertThat(updated).isOne();
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT to_char(expires_at AT TIME ZONE 'UTC', 'YYYY-MM-DD HH24:MI:SS.MS') "
                                    + "FROM identity.device_session WHERE refresh_token_hash = ?",
                            String.class,
                            refreshTokenGenerator.hash(expired.refreshToken())))
                    .isEqualTo("2026-08-15 12:00:00.750");
        });
        assertThat(persistedSession(expired.sessionId()).expiresAt()).isEqualTo(OBSERVED_AT);
        assertInvalidWithoutMutation(expired.refreshToken());

        var revoked = registerAndIssue(
                uuid("74000000-0000-4000-8000-000000000047"),
                uuid("75000000-0000-4000-8000-000000000057"),
                uuid("76000000-0000-4000-8000-000000000067"),
                uuid("77000000-0000-4000-8000-000000000077"),
                "revoked@example.com");
        runInTransaction(() -> {
            var updated = jdbcTemplate.update(
                    "UPDATE identity.device_session SET revoked_at = TIMESTAMPTZ '2026-08-15 12:00:00.750+00', "
                            + "revoke_reason = ? WHERE refresh_token_hash = ?",
                    "MANUAL",
                    refreshTokenGenerator.hash(revoked.refreshToken()));
            assertThat(updated).isOne();
        });
        assertInvalidWithoutMutation(revoked.refreshToken());

        var disabled = registerAndIssue(
                uuid("78000000-0000-4000-8000-000000000087"),
                uuid("79000000-0000-4000-8000-000000000097"),
                uuid("7a000000-0000-4000-8000-0000000000a7"),
                uuid("7b000000-0000-4000-8000-0000000000b7"),
                "disabled@example.com");
        runInTransaction(() -> {
            var updated = jdbcTemplate.update(
                    "UPDATE identity.user_account SET disabled_at = TIMESTAMPTZ '2026-08-15 11:59:59.750+00' WHERE id = ?",
                    disabled.userId());
            assertThat(updated).isOne();
        });
        assertInvalidWithoutMutation(disabled.refreshToken());
    }

    @Test
    void replacedTokenReuseCommitsFamilyRevocationBeforeRejectedOutcome() {
        var fixture = registerAndIssue(
                uuid("11000000-0000-4000-8000-000000000011"),
                uuid("12000000-0000-4000-8000-000000000012"),
                uuid("13000000-0000-4000-8000-000000000013"),
                uuid("14000000-0000-4000-8000-000000000014"),
                "reuse@example.com");
        var replacementId = uuid("15000000-0000-4000-8000-000000000015");
        var replacementAccessTokenId = uuid("16000000-0000-4000-8000-000000000016");
        idGenerator.setNextIds(replacementId, replacementAccessTokenId);
        var first = rotationService.rotate(fixture.refreshToken()).orElseThrow();
        idGenerator.reset();

        assertThat(rotationService.rotate(fixture.refreshToken())).isEmpty();

        assertThat(deviceSessionRepository.findByFamilyIdAndRevokedAtIsNull(fixture.sessionId()))
                .isEmpty();
        assertThat(persistedSession(replacementId).revokeReason()).isEqualTo("REUSE_DETECTED");
        assertThatThrownBy(() -> refreshAuthenticationService.authenticate(fixture.refreshToken()))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> refreshAuthenticationService.authenticate(first.refreshToken()))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> accessTokenConverter.convert(jwtDecoder.decode(first.accessToken())))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void jwtFailureRollsBackPredecessorMutationAndSuccessorInsert() {
        var fixture = registerAndIssue(
                uuid("21000000-0000-4000-8000-000000000021"),
                uuid("22000000-0000-4000-8000-000000000022"),
                uuid("23000000-0000-4000-8000-000000000023"),
                uuid("24000000-0000-4000-8000-000000000024"),
                "rollback@example.com");
        var replacementId = uuid("25000000-0000-4000-8000-000000000025");
        var failingAccessService = mock(AccessTokenIssuanceService.class);
        when(failingAccessService.issue(replacementId)).thenThrow(new IllegalStateException("jwt failure"));
        idGenerator.setNextIds(replacementId);
        var directService = new RefreshSessionRotationService(
                refreshTokenGenerator,
                deviceSessionRepository,
                userAccountRepository,
                failingAccessService,
                clock,
                idGenerator);

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
                        .execute(status -> directService.rotate(fixture.refreshToken())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("jwt failure");

        var state = persistedSession(fixture.sessionId());
        assertThat(state.lastUsedAt()).isNull();
        assertThat(state.revokedAt()).isNull();
        assertThat(state.replacedBySessionId()).isNull();
        assertThat(deviceSessionRepository.count()).isOne();
    }

    @Test
    void concurrentDuplicateRefreshLeavesNoActiveFamilyGeneration() throws Exception {
        var fixture = registerAndIssue(
                uuid("31000000-0000-4000-8000-000000000031"),
                uuid("32000000-0000-4000-8000-000000000032"),
                uuid("33000000-0000-4000-8000-000000000033"),
                uuid("34000000-0000-4000-8000-000000000034"),
                "concurrent@example.com");
        var replacementId = uuid("35000000-0000-4000-8000-000000000035");
        var replacementAccessTokenId = uuid("36000000-0000-4000-8000-000000000036");
        idGenerator.setNextIds(replacementId, replacementAccessTokenId);
        var rotationComplete = new CountDownLatch(1);
        var releaseFirstTransaction = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                var result = rotationService.rotate(fixture.refreshToken());
                rotationComplete.countDown();
                await(releaseFirstTransaction);
                return result;
            }));
            assertThat(rotationComplete.await(10, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> rotationService.rotate(fixture.refreshToken()));
            assertThat(waitingOnUserAccountLock())
                    .as("second refresh remains blocked on the owner row")
                    .isTrue();
            releaseFirstTransaction.countDown();

            var firstResult = first.get(10, TimeUnit.SECONDS);
            var secondResult = second.get(10, TimeUnit.SECONDS);
            assertThat(firstResult).isPresent();
            assertThat(secondResult).isEmpty();
            assertThat(deviceSessionRepository.findByFamilyIdAndRevokedAtIsNull(fixture.sessionId()))
                    .isEmpty();
            var winning = firstResult.orElseThrow();
            assertThatThrownBy(() -> refreshAuthenticationService.authenticate(winning.refreshToken()))
                    .isInstanceOf(AppException.class);
            assertThatThrownBy(() -> accessTokenConverter.convert(jwtDecoder.decode(winning.accessToken())))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            releaseFirstTransaction.countDown();
            executor.shutdownNow();
        }
    }

    private Fixture registerAndIssue(UUID userId, UUID authId, UUID sessionId, UUID accessTokenId, String email) {
        idGenerator.setNextIds(userId, authId, sessionId, accessTokenId);
        registrationService.register(email, RAW_PASSWORD);
        var issuedRefresh = issuanceService.issue(userId, "phone");
        var issuedAccess = accessTokenIssuanceService.issue(sessionId);
        return new Fixture(userId, issuedRefresh.sessionId(), issuedRefresh.refreshToken(), issuedAccess.accessToken());
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

    private void assertInvalidWithoutMutation(String rawRefreshToken) {
        var before = persistedDatabaseState();
        idGenerator.reset();
        assertThat(rotationService.rotate(rawRefreshToken)).isEmpty();
        assertThat(persistedDatabaseState()).isEqualTo(before);
        assertThat(idGenerator.consumedIds()).isEmpty();
    }

    private List<Map<String, Object>> persistedDatabaseState() {
        return List.of(Map.of(
                "users", jdbcTemplate.queryForList("SELECT * FROM identity.user_account ORDER BY id"),
                "auth", jdbcTemplate.queryForList("SELECT * FROM identity.auth_identity ORDER BY id"),
                "sessions", jdbcTemplate.queryForList("SELECT * FROM identity.device_session ORDER BY id")));
    }

    private boolean waitingOnUserAccountLock() {
        for (var attempt = 0; attempt < 500; attempt++) {
            var waiting = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity a "
                            + "WHERE a.wait_event_type = 'Lock' "
                            + "AND cardinality(pg_blocking_pids(a.pid)) > 0 "
                            + "AND a.query ILIKE '%user_account%'",
                    Long.class);
            if (waiting != null && waiting > 0) {
                return true;
            }
            LockSupport.parkNanos(Duration.ofMillis(10).toNanos());
        }
        return false;
    }

    private void runInTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent refresh test");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted concurrent refresh test", exception);
        }
    }

    private static UUID uuid(String value) {
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
        private final Deque<UUID> consumedIds = new ArrayDeque<>();

        void setNextIds(UUID... ids) {
            nextIds.clear();
            nextIds.addAll(Arrays.asList(ids));
        }

        void reset() {
            setNextIds();
            consumedIds.clear();
        }

        synchronized Deque<UUID> consumedIds() {
            return new ArrayDeque<>(consumedIds);
        }

        @Override
        public synchronized UUID next() {
            var id = nextIds.removeFirst();
            consumedIds.addLast(id);
            return id;
        }
    }

    private record Fixture(UUID userId, UUID sessionId, String refreshToken, String accessToken) {}

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
}
