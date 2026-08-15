package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import dev.canverse.stocks.identity.application.AccessTokenIssuanceService;
import dev.canverse.stocks.identity.application.RefreshSessionRotationService;
import dev.canverse.stocks.identity.infrastructure.DeviceSessionRepository;
import dev.canverse.stocks.identity.infrastructure.RefreshSessionOwnerProjection;
import dev.canverse.stocks.identity.infrastructure.SecureRefreshTokenGenerator;
import dev.canverse.stocks.identity.infrastructure.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshSessionRotationControlFlowTest {

    @Test
    void nullTokenFailsBeforeCollaboratorWork() {
        var refreshTokenGenerator = mock(SecureRefreshTokenGenerator.class);
        var deviceSessionRepository = mock(DeviceSessionRepository.class);
        var userAccountRepository = mock(UserAccountRepository.class);
        var accessTokenIssuanceService = mock(AccessTokenIssuanceService.class);
        var clock = mock(Clock.class);
        var idGenerator = mock(dev.canverse.stocks.platform.id.IdGenerator.class);
        var service = new RefreshSessionRotationService(
                refreshTokenGenerator,
                deviceSessionRepository,
                userAccountRepository,
                accessTokenIssuanceService,
                clock,
                idGenerator);

        assertThatThrownBy(() -> service.rotate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("rawRefreshToken");

        verifyNoInteractions(
                refreshTokenGenerator,
                deviceSessionRepository,
                userAccountRepository,
                accessTokenIssuanceService,
                clock,
                idGenerator);
    }

    @Test
    void unknownHashReturnsRejectedBeforeOwnerLockOrGeneration() {
        var refreshTokenGenerator = mock(SecureRefreshTokenGenerator.class);
        var deviceSessionRepository = mock(DeviceSessionRepository.class);
        var userAccountRepository = mock(UserAccountRepository.class);
        var accessTokenIssuanceService = mock(AccessTokenIssuanceService.class);
        var clock = mock(Clock.class);
        var idGenerator = mock(dev.canverse.stocks.platform.id.IdGenerator.class);
        when(refreshTokenGenerator.hash("presented-token")).thenReturn("stored-hash");
        when(deviceSessionRepository.findRefreshSessionOwnerByRefreshTokenHash("stored-hash"))
                .thenReturn(Optional.empty());
        var service = new RefreshSessionRotationService(
                refreshTokenGenerator,
                deviceSessionRepository,
                userAccountRepository,
                accessTokenIssuanceService,
                clock,
                idGenerator);

        assertThat(service.rotate("presented-token")).isEmpty();

        var ordered = inOrder(refreshTokenGenerator, deviceSessionRepository);
        ordered.verify(refreshTokenGenerator).hash("presented-token");
        ordered.verify(deviceSessionRepository).findRefreshSessionOwnerByRefreshTokenHash("stored-hash");
        verifyNoMoreInteractions(
                refreshTokenGenerator,
                deviceSessionRepository,
                userAccountRepository,
                accessTokenIssuanceService,
                clock,
                idGenerator);
    }

    @Test
    void knownHashLocksOwnerReloadsSessionThenObservesClock() {
        var refreshTokenGenerator = mock(SecureRefreshTokenGenerator.class);
        var deviceSessionRepository = mock(DeviceSessionRepository.class);
        var userAccountRepository = mock(UserAccountRepository.class);
        var accessTokenIssuanceService = mock(AccessTokenIssuanceService.class);
        var clock = mock(Clock.class);
        var idGenerator = mock(dev.canverse.stocks.platform.id.IdGenerator.class);
        var sessionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        when(refreshTokenGenerator.hash("presented-token")).thenReturn("stored-hash");
        when(deviceSessionRepository.findRefreshSessionOwnerByRefreshTokenHash("stored-hash"))
                .thenReturn(Optional.of(new RefreshSessionOwnerProjection(sessionId, userId)));
        when(userAccountRepository.findByIdForUpdate(userId)).thenReturn(Optional.empty());
        when(deviceSessionRepository.findById(sessionId)).thenReturn(Optional.empty());
        when(clock.instant()).thenReturn(Instant.parse("2026-08-15T12:00:00Z"));
        var service = new RefreshSessionRotationService(
                refreshTokenGenerator,
                deviceSessionRepository,
                userAccountRepository,
                accessTokenIssuanceService,
                clock,
                idGenerator);

        assertThat(service.rotate("presented-token")).isEmpty();

        var ordered = inOrder(refreshTokenGenerator, deviceSessionRepository, userAccountRepository, clock);
        ordered.verify(refreshTokenGenerator).hash("presented-token");
        ordered.verify(deviceSessionRepository).findRefreshSessionOwnerByRefreshTokenHash("stored-hash");
        ordered.verify(userAccountRepository).findByIdForUpdate(userId);
        ordered.verify(deviceSessionRepository).findById(sessionId);
        ordered.verify(clock).instant();
        verifyNoMoreInteractions(
                refreshTokenGenerator,
                deviceSessionRepository,
                userAccountRepository,
                accessTokenIssuanceService,
                clock,
                idGenerator);
    }
}
