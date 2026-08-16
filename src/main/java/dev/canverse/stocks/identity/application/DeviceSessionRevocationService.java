package dev.canverse.stocks.identity.application;

import dev.canverse.stocks.identity.domain.DeviceSession;
import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.identity.infrastructure.DeviceSessionRepository;
import dev.canverse.stocks.identity.infrastructure.UserAccountRepository;
import dev.canverse.stocks.platform.application.SecurityEventRecorder;
import dev.canverse.stocks.platform.error.AppException;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceSessionRevocationService {

    private final UserAccountRepository userAccountRepository;
    private final DeviceSessionRepository deviceSessionRepository;
    private final SecurityEventRecorder securityEventRecorder;
    private final Clock clock;

    @Transactional
    public void logoutCurrentSession(UUID userAccountId, UUID currentSessionId) {
        Objects.requireNonNull(userAccountId, "userAccountId");
        Objects.requireNonNull(currentSessionId, "currentSessionId");

        var owner = userAccountRepository
                .findByIdForUpdate(userAccountId)
                .orElseThrow(() -> new AppException(IdentityErrorCode.INVALID_CREDENTIALS));
        if (owner.getDisabledAt() != null) {
            throw new AppException(IdentityErrorCode.INVALID_CREDENTIALS);
        }

        var currentSession = deviceSessionRepository
                .findOwnedById(currentSessionId, userAccountId)
                .orElseThrow(() -> new AppException(IdentityErrorCode.INVALID_CREDENTIALS));

        var familyId = currentSession.getFamilyId();
        var observedAt = clock.instant();
        var terminalSession = deviceSessionRepository
                .findTerminalByUserAccountIdAndFamilyId(userAccountId, familyId)
                .orElseThrow(() -> new IllegalStateException("Missing terminal generation for family " + familyId));

        if (terminalSession.getRevokedAt() == null) {
            terminalSession.revokeTerminal(DeviceSession.USER_LOGOUT_REVOKE_REASON, observedAt);
            deviceSessionRepository.saveAndFlush(terminalSession);
            securityEventRecorder.recordAt(
                    userAccountId,
                    SecurityEventRecorder.CURRENT_SESSION_LOGGED_OUT,
                    Map.of("familyId", familyId.toString()),
                    observedAt);
        }
    }

    @Transactional
    public void logoutAllSessions(UUID userAccountId) {
        Objects.requireNonNull(userAccountId, "userAccountId");

        var owner = userAccountRepository
                .findByIdForUpdate(userAccountId)
                .orElseThrow(() -> new AppException(IdentityErrorCode.INVALID_CREDENTIALS));
        if (owner.getDisabledAt() != null) {
            throw new AppException(IdentityErrorCode.INVALID_CREDENTIALS);
        }

        var observedAt = clock.instant();
        var terminalSessions = deviceSessionRepository.findTerminalSessionsByUserAccountId(userAccountId);

        var revokedCount = 0;
        for (var session : terminalSessions) {
            if (session.getRevokedAt() == null) {
                session.revokeTerminal(DeviceSession.USER_LOGOUT_ALL_REVOKE_REASON, observedAt);
                deviceSessionRepository.save(session);
                revokedCount++;
            }
        }
        deviceSessionRepository.flush();

        if (revokedCount > 0) {
            securityEventRecorder.recordAt(
                    userAccountId,
                    SecurityEventRecorder.ALL_SESSIONS_LOGGED_OUT,
                    Map.of("revokedFamilyCount", revokedCount),
                    observedAt);
        }
    }

    @Transactional
    public boolean revokeSelectedFamily(UUID userAccountId, UUID currentSessionId, UUID targetFamilyId) {
        Objects.requireNonNull(userAccountId, "userAccountId");
        Objects.requireNonNull(currentSessionId, "currentSessionId");
        Objects.requireNonNull(targetFamilyId, "targetFamilyId");

        var owner = userAccountRepository
                .findByIdForUpdate(userAccountId)
                .orElseThrow(() -> new AppException(IdentityErrorCode.INVALID_CREDENTIALS));
        if (owner.getDisabledAt() != null) {
            throw new AppException(IdentityErrorCode.INVALID_CREDENTIALS);
        }

        var currentSession = deviceSessionRepository
                .findOwnedById(currentSessionId, userAccountId)
                .orElseThrow(() -> new AppException(IdentityErrorCode.INVALID_CREDENTIALS));

        var isCurrentFamily = targetFamilyId.equals(currentSession.getFamilyId());
        var observedAt = clock.instant();

        var terminalSession = deviceSessionRepository
                .findTerminalByUserAccountIdAndFamilyId(userAccountId, targetFamilyId)
                .orElseThrow(() -> {
                    if (!deviceSessionRepository.existsByUserAccountIdAndFamilyId(userAccountId, targetFamilyId)) {
                        return new AppException(IdentityErrorCode.SESSION_NOT_FOUND);
                    }
                    return new IllegalStateException("Missing terminal generation for family " + targetFamilyId);
                });
        if (terminalSession.getRevokedAt() == null) {
            terminalSession.revokeTerminal(DeviceSession.USER_REVOKED_REVOKE_REASON, observedAt);
            deviceSessionRepository.saveAndFlush(terminalSession);
            securityEventRecorder.recordAt(
                    userAccountId,
                    SecurityEventRecorder.DEVICE_SESSION_REVOKED,
                    Map.of("familyId", targetFamilyId.toString()),
                    observedAt);
        }

        return isCurrentFamily;
    }
}
