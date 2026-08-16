package dev.canverse.stocks.identity.application;

import dev.canverse.stocks.identity.domain.DeviceSession;
import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.identity.infrastructure.DeviceSessionRepository;
import dev.canverse.stocks.identity.infrastructure.SecureRefreshTokenGenerator;
import dev.canverse.stocks.platform.error.AppException;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshSessionAuthenticationService {

    private final SecureRefreshTokenGenerator refreshTokenGenerator;
    private final DeviceSessionRepository deviceSessionRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public UUID authenticate(String rawRefreshToken) {
        Objects.requireNonNull(rawRefreshToken, "rawRefreshToken");

        var observedAt = clock.instant();
        var refreshTokenHash = refreshTokenGenerator.hash(rawRefreshToken);
        return deviceSessionRepository
                .findByRefreshTokenHash(refreshTokenHash)
                .filter(session -> session.isActiveAndUserEnabled(observedAt))
                .map(DeviceSession::getId)
                .orElseThrow(() -> new AppException(IdentityErrorCode.INVALID_CREDENTIALS));
    }
}
