package dev.canverse.stocks.identity.application;

import dev.canverse.stocks.identity.infrastructure.DeviceSessionRepository;
import dev.canverse.stocks.identity.infrastructure.SecureRefreshTokenGenerator;
import dev.canverse.stocks.identity.infrastructure.UserAccountRepository;
import dev.canverse.stocks.platform.application.SecurityEventRecorder;
import dev.canverse.stocks.platform.id.IdGenerator;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshSessionRotationService {

    private final SecureRefreshTokenGenerator refreshTokenGenerator;
    private final DeviceSessionRepository deviceSessionRepository;
    private final UserAccountRepository userAccountRepository;
    private final AccessTokenIssuanceService accessTokenIssuanceService;
    private final SecurityEventRecorder securityEventRecorder;
    private final Clock clock;
    private final IdGenerator idGenerator;

    @Transactional
    public Optional<LocalRefreshResult> rotate(String rawRefreshToken) {
        Objects.requireNonNull(rawRefreshToken, "rawRefreshToken");

        var refreshTokenHash = refreshTokenGenerator.hash(rawRefreshToken);
        var ownerProjection = deviceSessionRepository.findRefreshSessionOwnerByRefreshTokenHash(refreshTokenHash);
        if (ownerProjection.isEmpty()) {
            return Optional.empty();
        }

        var owner =
                userAccountRepository.findByIdForUpdate(ownerProjection.get().userAccountId());
        var deviceSession =
                deviceSessionRepository.findById(ownerProjection.get().sessionId());
        var observedAt = clock.instant();
        if (owner.isEmpty() || deviceSession.isEmpty()) {
            return Optional.empty();
        }

        var session = deviceSession.get();
        if (owner.get().getDisabledAt() != null || session.getUserAccount().getDisabledAt() != null) {
            return Optional.empty();
        }
        if (session.getReplacedBySessionId() != null) {
            deviceSessionRepository
                    .findByFamilyIdAndRevokedAtIsNull(session.getFamilyId())
                    .ifPresent(activeSession -> {
                        activeSession.revokeForReuse(observedAt);
                        deviceSessionRepository.saveAndFlush(activeSession);
                        securityEventRecorder.recordAt(
                                ownerProjection.get().userAccountId(),
                                SecurityEventRecorder.REFRESH_REUSE_DETECTED,
                                Map.of(
                                        "familyId", session.getFamilyId().toString(),
                                        "sessionId", session.getId().toString()),
                                observedAt);
                        deviceSessionRepository.flush();
                    });
            return Optional.empty();
        }
        if (session.getRevokedAt() != null || !session.getExpiresAt().isAfter(observedAt)) {
            return Optional.empty();
        }

        var replacementToken = refreshTokenGenerator.generate();
        var replacementId = idGenerator.next();
        var replacement = session.createReplacement(replacementId, replacementToken.hash(), observedAt);
        session.consumeForRotation(observedAt);
        deviceSessionRepository.saveAndFlush(session);
        deviceSessionRepository.saveAndFlush(replacement);
        session.linkReplacement(replacementId);
        deviceSessionRepository.saveAndFlush(session);

        var accessToken = accessTokenIssuanceService.issue(replacementId);
        return Optional.of(new LocalRefreshResult(
                replacementId,
                accessToken.accessToken(),
                accessToken.expiresAt(),
                replacement.getExpiresAt(),
                replacementToken.rawToken()));
    }
}
