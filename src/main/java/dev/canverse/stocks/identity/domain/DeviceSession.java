package dev.canverse.stocks.identity.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "device_session", schema = "identity")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceSession {

    public static final String ROTATED_REVOKE_REASON = "ROTATED";
    public static final String REUSE_DETECTED_REVOKE_REASON = "REUSE_DETECTED";
    public static final String USER_LOGOUT_REVOKE_REASON = "USER_LOGOUT";
    public static final String USER_LOGOUT_ALL_REVOKE_REASON = "USER_LOGOUT_ALL";
    public static final String USER_REVOKED_REVOKE_REASON = "USER_REVOKED";

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id")
    private UserAccount userAccount;

    private UUID familyId;

    private String refreshTokenHash;

    private String deviceLabel;

    private Instant createdAt;

    private Instant lastUsedAt;

    private Instant expiresAt;

    private Instant revokedAt;

    private String revokeReason;

    private UUID replacedBySessionId;

    public static DeviceSession initialGeneration(
            UUID id,
            UserAccount userAccount,
            String refreshTokenHash,
            String deviceLabel,
            Instant createdAt,
            Instant expiresAt) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userAccount, "userAccount");
        Objects.requireNonNull(refreshTokenHash, "refreshTokenHash");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }

        var deviceSession = new DeviceSession();
        deviceSession.id = id;
        deviceSession.userAccount = userAccount;
        deviceSession.familyId = id;
        deviceSession.refreshTokenHash = refreshTokenHash;
        deviceSession.deviceLabel = deviceLabel;
        deviceSession.createdAt = createdAt;
        deviceSession.lastUsedAt = null;
        deviceSession.expiresAt = expiresAt;
        deviceSession.revokedAt = null;
        deviceSession.revokeReason = null;
        deviceSession.replacedBySessionId = null;
        return deviceSession;
    }

    public DeviceSession rotate(UUID replacementId, String replacementRefreshTokenHash, Instant observedAt) {
        var replacement = createReplacement(replacementId, replacementRefreshTokenHash, observedAt);
        consumeForRotation(observedAt);
        linkReplacement(replacementId);
        return replacement;
    }

    public DeviceSession createReplacement(UUID replacementId, String replacementRefreshTokenHash, Instant observedAt) {
        Objects.requireNonNull(replacementId, "replacementId");
        Objects.requireNonNull(replacementRefreshTokenHash, "replacementRefreshTokenHash");
        Objects.requireNonNull(observedAt, "observedAt");
        validateRotatable(replacementId, observedAt);
        return replacementGeneration(replacementId, replacementRefreshTokenHash, observedAt);
    }

    public void consumeForRotation(Instant observedAt) {
        Objects.requireNonNull(observedAt, "observedAt");
        validateRotatable(null, observedAt);
        lastUsedAt = observedAt;
        revokedAt = observedAt;
        revokeReason = ROTATED_REVOKE_REASON;
    }

    public void linkReplacement(UUID replacementId) {
        Objects.requireNonNull(replacementId, "replacementId");
        if (id.equals(replacementId)) {
            throw new IllegalArgumentException("A replacement generation requires a fresh id");
        }
        if (!ROTATED_REVOKE_REASON.equals(revokeReason) || revokedAt == null) {
            throw new IllegalStateException("Only a consumed generation can be linked");
        }
        if (replacedBySessionId != null) {
            throw new IllegalStateException("A generation can only have one replacement");
        }
        replacedBySessionId = replacementId;
    }

    public void revokeForReuse(Instant observedAt) {
        Objects.requireNonNull(observedAt, "observedAt");
        if (revokedAt == null && replacedBySessionId == null) {
            revokedAt = observedAt;
            revokeReason = REUSE_DETECTED_REVOKE_REASON;
        }
    }

    public void revokeTerminal(String reason, Instant observedAt) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(observedAt, "observedAt");
        if (!isUserRevocationReason(reason)) {
            throw new IllegalArgumentException("Unsupported terminal revocation reason: " + reason);
        }
        if (replacedBySessionId != null) {
            throw new IllegalStateException("A historical replaced generation cannot be terminally revoked");
        }
        if (revokedAt == null) {
            revokedAt = observedAt;
            revokeReason = reason;
        }
    }

    private boolean isUserRevocationReason(String reason) {
        return USER_LOGOUT_REVOKE_REASON.equals(reason)
                || USER_LOGOUT_ALL_REVOKE_REASON.equals(reason)
                || USER_REVOKED_REVOKE_REASON.equals(reason);
    }

    private DeviceSession replacementGeneration(
            UUID replacementId, String replacementRefreshTokenHash, Instant createdAt) {
        var replacement = new DeviceSession();
        replacement.id = replacementId;
        replacement.userAccount = userAccount;
        replacement.familyId = familyId;
        replacement.refreshTokenHash = replacementRefreshTokenHash;
        replacement.deviceLabel = deviceLabel;
        replacement.createdAt = createdAt;
        replacement.lastUsedAt = null;
        replacement.expiresAt = expiresAt;
        replacement.revokedAt = null;
        replacement.revokeReason = null;
        replacement.replacedBySessionId = null;
        return replacement;
    }

    private void validateRotatable(UUID replacementId, Instant observedAt) {
        if (replacementId != null && id.equals(replacementId)) {
            throw new IllegalArgumentException("A replacement generation requires a fresh id");
        }
        if (revokedAt != null || replacedBySessionId != null) {
            throw new IllegalStateException("Only an active generation can be rotated");
        }
        if (!expiresAt.isAfter(observedAt)) {
            throw new IllegalStateException("An expired generation cannot be rotated");
        }
    }
}
