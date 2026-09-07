package dev.canverse.stocks.identity.application;

import dev.canverse.stocks.identity.application.model.LocalRefreshResult;
import dev.canverse.stocks.identity.domain.DeviceSession;
import dev.canverse.stocks.identity.domain.UserAccount;
import dev.canverse.stocks.identity.infrastructure.DeviceSessionRepository;
import dev.canverse.stocks.identity.infrastructure.RefreshSessionOwnerProjection;
import dev.canverse.stocks.identity.infrastructure.SecureRefreshTokenGenerator;
import dev.canverse.stocks.identity.infrastructure.UserAccountRepository;
import dev.canverse.stocks.platform.application.SecurityEventRecorder;
import dev.canverse.stocks.platform.id.IdGenerator;
import java.time.Clock;
import java.time.Instant;
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
        return deviceSessionRepository.findRefreshSessionOwnerByRefreshTokenHash(refreshTokenHash).flatMap(this::rotateKnownSession);
    }

    private Optional<LocalRefreshResult> rotateKnownSession(RefreshSessionOwnerProjection ownerProjection) {
        var owner = userAccountRepository.findByIdForUpdate(ownerProjection.userAccountId());
        var session = deviceSessionRepository.findById(ownerProjection.sessionId());
        var observedAt = clock.instant();
        return owner.flatMap(user -> session.flatMap(deviceSession -> rotateLocked(ownerProjection, user, deviceSession, observedAt)));
    }

    private Optional<LocalRefreshResult> rotateLocked(RefreshSessionOwnerProjection ownerProjection, UserAccount owner, DeviceSession session,
            Instant observedAt) {
        if (owner.getDisabledAt() != null || session.getUserAccount().getDisabledAt() != null) {
            return Optional.empty();
        }
        if (session.getReplacedBySessionId() != null) {
            deviceSessionRepository.findByFamilyIdAndRevokedAtIsNull(session.getFamilyId()).ifPresent(activeSession -> {
                activeSession.revokeForReuse(observedAt);
                deviceSessionRepository.saveAndFlush(activeSession);
                securityEventRecorder.recordAt(ownerProjection.userAccountId(), SecurityEventRecorder.REFRESH_REUSE_DETECTED,
                        Map.of("familyId", session.getFamilyId().toString(), "sessionId", session.getId().toString()), observedAt);
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
        // The predecessor must release the active-family index before the replacement
        // is inserted.
        deviceSessionRepository.saveAndFlush(session);
        deviceSessionRepository.saveAndFlush(replacement);
        // Link only after the replacement exists because the database enforces this
        // foreign key.
        session.linkReplacement(replacementId);

        var accessToken = accessTokenIssuanceService.issue(replacementId);
        return Optional.of(new LocalRefreshResult(replacementId, accessToken.accessToken(), accessToken.expiresAt(), replacement.getExpiresAt(),
                replacementToken.rawToken()));
    }
}
