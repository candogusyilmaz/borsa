package dev.canverse.stocks.identity.application;

import dev.canverse.stocks.identity.application.model.IssuedRefreshSession;
import dev.canverse.stocks.identity.configuration.RefreshSessionProperties;
import dev.canverse.stocks.identity.domain.DeviceSession;
import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.identity.infrastructure.DeviceSessionRepository;
import dev.canverse.stocks.identity.infrastructure.SecureRefreshTokenGenerator;
import dev.canverse.stocks.identity.infrastructure.UserAccountRepository;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.id.IdGenerator;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshSessionIssuanceService {

    private final UserAccountRepository userAccountRepository;
    private final DeviceSessionRepository deviceSessionRepository;
    private final SecureRefreshTokenGenerator refreshTokenGenerator;
    private final RefreshSessionProperties refreshSessionProperties;
    private final Clock clock;
    private final IdGenerator idGenerator;

    @Transactional
    public IssuedRefreshSession issue(UUID userAccountId, String deviceLabel) {
        Objects.requireNonNull(userAccountId, "userAccountId");

        var userAccount = userAccountRepository.findById(userAccountId).filter(account -> account.getDisabledAt() == null)
                .orElseThrow(() -> new AppException(IdentityErrorCode.INVALID_CREDENTIALS));

        var generatedToken = refreshTokenGenerator.generate();
        var sessionId = idGenerator.next();
        var createdAt = clock.instant();
        var expiresAt = createdAt.plus(refreshSessionProperties.lifetime());
        var deviceSession = DeviceSession.initialGeneration(sessionId, userAccount, generatedToken.hash(), deviceLabel, createdAt, expiresAt);

        deviceSessionRepository.saveAndFlush(deviceSession);

        return new IssuedRefreshSession(sessionId, deviceSession.getFamilyId(), generatedToken.rawToken(), expiresAt);
    }
}
