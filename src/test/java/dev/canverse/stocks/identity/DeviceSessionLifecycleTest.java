package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.canverse.stocks.identity.application.DeviceSessionQueryService;
import dev.canverse.stocks.identity.domain.DeviceSession;
import dev.canverse.stocks.identity.domain.UserAccount;
import dev.canverse.stocks.identity.infrastructure.DeviceSessionFamilyRecord;
import dev.canverse.stocks.identity.infrastructure.DeviceSessionReadRepository;
import dev.canverse.stocks.identity.output.DeviceSessionStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DeviceSessionLifecycleTest {

    private final Instant createdAt = Instant.parse("2026-08-15T10:00:00Z");
    private final Instant expiresAt = Instant.parse("2026-09-14T10:00:00Z");

    @Test
    void revokeTerminalSetsExactTimeAndReason() {
        var user = UserAccount.register(UUID.randomUUID(), "test@example.com", "test@example.com", createdAt);
        var session =
                DeviceSession.initialGeneration(UUID.randomUUID(), user, "hash1", "desktop", createdAt, expiresAt);

        var observedAt = Instant.parse("2026-08-15T12:00:00Z");
        session.revokeTerminal(DeviceSession.USER_LOGOUT_REVOKE_REASON, observedAt);

        assertThat(session.getRevokedAt()).isEqualTo(observedAt);
        assertThat(session.getRevokeReason()).isEqualTo(DeviceSession.USER_LOGOUT_REVOKE_REASON);
    }

    @Test
    void activeAndUserEnabledPredicateRejectsRevokedOrExpiredSessions() {
        var user = UserAccount.register(UUID.randomUUID(), "test@example.com", "test@example.com", createdAt);
        var session =
                DeviceSession.initialGeneration(UUID.randomUUID(), user, "hash1", "desktop", createdAt, expiresAt);
        var observedAt = Instant.parse("2026-08-15T12:00:00Z");

        assertThat(session.isActiveAndUserEnabled(observedAt)).isTrue();

        session.revokeForReuse(observedAt);
        assertThat(session.isActiveAndUserEnabled(observedAt)).isFalse();

        var expiredSession =
                DeviceSession.initialGeneration(UUID.randomUUID(), user, "hash2", "desktop", createdAt, observedAt);
        assertThat(expiredSession.isActiveAndUserEnabled(observedAt)).isFalse();
    }

    @Test
    void revokeTerminalIsIdempotentAndDoesNotOverwriteEarlierRevocation() {
        var user = UserAccount.register(UUID.randomUUID(), "test@example.com", "test@example.com", createdAt);
        var session =
                DeviceSession.initialGeneration(UUID.randomUUID(), user, "hash1", "desktop", createdAt, expiresAt);

        var firstTime = Instant.parse("2026-08-15T12:00:00Z");
        session.revokeTerminal(DeviceSession.USER_LOGOUT_REVOKE_REASON, firstTime);

        var secondTime = Instant.parse("2026-08-15T13:00:00Z");
        session.revokeTerminal(DeviceSession.USER_REVOKED_REVOKE_REASON, secondTime);

        assertThat(session.getRevokedAt()).isEqualTo(firstTime);
        assertThat(session.getRevokeReason()).isEqualTo(DeviceSession.USER_LOGOUT_REVOKE_REASON);
    }

    @Test
    void revokeTerminalCannotOverwriteRotatedOrReuseDetectedReason() {
        var user = UserAccount.register(UUID.randomUUID(), "test@example.com", "test@example.com", createdAt);
        var session =
                DeviceSession.initialGeneration(UUID.randomUUID(), user, "hash1", "desktop", createdAt, expiresAt);

        var rotateTime = Instant.parse("2026-08-15T11:00:00Z");
        session.consumeForRotation(rotateTime);
        session.linkReplacement(UUID.randomUUID());

        var logoutTime = Instant.parse("2026-08-15T12:00:00Z");
        assertThatThrownBy(() -> session.revokeTerminal(DeviceSession.USER_LOGOUT_REVOKE_REASON, logoutTime))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("replaced generation");
    }

    @Test
    void statusDerivationActiveWhenUnrevokedAndBeforeExpiry() {
        var observedAt = Instant.parse("2026-08-15T12:00:00Z");
        var readRepository = Mockito.mock(DeviceSessionReadRepository.class);
        var clock = Clock.fixed(observedAt, ZoneOffset.UTC);
        var queryService = new DeviceSessionQueryService(readRepository, clock);

        var familyId = UUID.randomUUID();
        var record = new DeviceSessionFamilyRecord(
                familyId, UUID.randomUUID(), "phone", createdAt, null, expiresAt, expiresAt, null, null, true);
        Mockito.when(readRepository.findFamilyDetail(Mockito.any(), Mockito.any(), Mockito.eq(familyId)))
                .thenReturn(Optional.of(record));

        var response = queryService.getSessionDetail(UUID.randomUUID(), UUID.randomUUID(), familyId);
        assertThat(response.status()).isEqualTo(DeviceSessionStatus.ACTIVE);
        assertThat(response.endedAt()).isNull();
        assertThat(response.current()).isTrue();
    }

    @Test
    void statusDerivationExpiredWhenObservedAtOrAfterExpiry() {
        var observedAt = expiresAt; // exact equality is expired
        var readRepository = Mockito.mock(DeviceSessionReadRepository.class);
        var clock = Clock.fixed(observedAt, ZoneOffset.UTC);
        var queryService = new DeviceSessionQueryService(readRepository, clock);

        var familyId = UUID.randomUUID();
        var record = new DeviceSessionFamilyRecord(
                familyId, UUID.randomUUID(), "phone", createdAt, null, expiresAt, expiresAt, null, null, false);
        Mockito.when(readRepository.findFamilyDetail(Mockito.any(), Mockito.any(), Mockito.eq(familyId)))
                .thenReturn(Optional.of(record));

        var response = queryService.getSessionDetail(UUID.randomUUID(), UUID.randomUUID(), familyId);
        assertThat(response.status()).isEqualTo(DeviceSessionStatus.EXPIRED);
        assertThat(response.endedAt()).isEqualTo(expiresAt);
    }

    @Test
    void statusDerivationRevokedWhenTerminalGenerationRevoked() {
        var observedAt = Instant.parse("2026-08-15T12:00:00Z");
        var revokedAt = Instant.parse("2026-08-15T11:00:00Z");
        var readRepository = Mockito.mock(DeviceSessionReadRepository.class);
        var clock = Clock.fixed(observedAt, ZoneOffset.UTC);
        var queryService = new DeviceSessionQueryService(readRepository, clock);

        var familyId = UUID.randomUUID();
        var record = new DeviceSessionFamilyRecord(
                familyId,
                UUID.randomUUID(),
                "phone",
                createdAt,
                null,
                expiresAt,
                expiresAt,
                revokedAt,
                DeviceSession.USER_LOGOUT_REVOKE_REASON,
                false);
        Mockito.when(readRepository.findFamilyDetail(Mockito.any(), Mockito.any(), Mockito.eq(familyId)))
                .thenReturn(Optional.of(record));

        var response = queryService.getSessionDetail(UUID.randomUUID(), UUID.randomUUID(), familyId);
        assertThat(response.status()).isEqualTo(DeviceSessionStatus.REVOKED);
        assertThat(response.endedAt()).isEqualTo(revokedAt);
    }

    @Test
    void statusDerivationCompromisedWhenTerminalGenerationReuseDetected() {
        var observedAt = Instant.parse("2026-08-15T12:00:00Z");
        var revokedAt = Instant.parse("2026-08-15T11:00:00Z");
        var readRepository = Mockito.mock(DeviceSessionReadRepository.class);
        var clock = Clock.fixed(observedAt, ZoneOffset.UTC);
        var queryService = new DeviceSessionQueryService(readRepository, clock);

        var familyId = UUID.randomUUID();
        var record = new DeviceSessionFamilyRecord(
                familyId,
                UUID.randomUUID(),
                "phone",
                createdAt,
                null,
                expiresAt,
                expiresAt,
                revokedAt,
                DeviceSession.REUSE_DETECTED_REVOKE_REASON,
                false);
        Mockito.when(readRepository.findFamilyDetail(Mockito.any(), Mockito.any(), Mockito.eq(familyId)))
                .thenReturn(Optional.of(record));

        var response = queryService.getSessionDetail(UUID.randomUUID(), UUID.randomUUID(), familyId);
        assertThat(response.status()).isEqualTo(DeviceSessionStatus.COMPROMISED);
        assertThat(response.endedAt()).isEqualTo(revokedAt);
    }

    @Test
    void inconsistentFamilyExpiryThrowsInternalException() {
        var observedAt = Instant.parse("2026-08-15T12:00:00Z");
        var readRepository = Mockito.mock(DeviceSessionReadRepository.class);
        var clock = Clock.fixed(observedAt, ZoneOffset.UTC);
        var queryService = new DeviceSessionQueryService(readRepository, clock);

        var familyId = UUID.randomUUID();
        var record = new DeviceSessionFamilyRecord(
                familyId,
                UUID.randomUUID(),
                "phone",
                createdAt,
                null,
                expiresAt,
                expiresAt.plusSeconds(3600), // Inconsistent
                null,
                null,
                false);
        Mockito.when(readRepository.findFamilyDetail(Mockito.any(), Mockito.any(), Mockito.eq(familyId)))
                .thenReturn(Optional.of(record));

        assertThatThrownBy(() -> queryService.getSessionDetail(UUID.randomUUID(), UUID.randomUUID(), familyId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Inconsistent family expiry");
    }
}
