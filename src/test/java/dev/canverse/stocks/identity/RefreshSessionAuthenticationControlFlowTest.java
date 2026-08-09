package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import dev.canverse.stocks.identity.application.RefreshSessionAuthenticationService;
import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.identity.infrastructure.DeviceSessionRepository;
import dev.canverse.stocks.identity.infrastructure.SecureRefreshTokenGenerator;
import dev.canverse.stocks.platform.error.AppException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RefreshSessionAuthenticationControlFlowTest {

    @Test
    void nullTokenFailsBeforeTimeHashOrLookupWork() {
        var refreshTokenGenerator = mock(SecureRefreshTokenGenerator.class);
        var deviceSessionRepository = mock(DeviceSessionRepository.class);
        var clock = mock(Clock.class);
        var service = new RefreshSessionAuthenticationService(refreshTokenGenerator, deviceSessionRepository, clock);

        assertThatThrownBy(() -> service.authenticate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("rawRefreshToken");

        verifyNoInteractions(clock, refreshTokenGenerator, deviceSessionRepository);
    }

    @Test
    void nonNullTokenObservesTimeHashesAndLooksUpExactlyOnceInOrder() {
        var rawRefreshToken = "presented-refresh-token";
        var refreshTokenHash = "stored-refresh-token-hash";
        var observedAt = Instant.parse("2026-08-09T12:00:00Z");
        var refreshTokenGenerator = mock(SecureRefreshTokenGenerator.class);
        var deviceSessionRepository = mock(DeviceSessionRepository.class);
        var clock = mock(Clock.class);
        when(clock.instant()).thenReturn(observedAt);
        when(refreshTokenGenerator.hash(rawRefreshToken)).thenReturn(refreshTokenHash);
        when(deviceSessionRepository.findByRefreshTokenHash(refreshTokenHash)).thenReturn(Optional.empty());
        var service = new RefreshSessionAuthenticationService(refreshTokenGenerator, deviceSessionRepository, clock);

        var thrown = catchThrowable(() -> service.authenticate(rawRefreshToken));

        assertThat(thrown).isExactlyInstanceOf(AppException.class);
        assertThat(((AppException) thrown).getErrorCode()).isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS);
        var ordered = inOrder(clock, refreshTokenGenerator, deviceSessionRepository);
        ordered.verify(clock).instant();
        ordered.verify(refreshTokenGenerator).hash(rawRefreshToken);
        ordered.verify(deviceSessionRepository).findByRefreshTokenHash(refreshTokenHash);
        verifyNoMoreInteractions(clock, refreshTokenGenerator, deviceSessionRepository);
    }
}
