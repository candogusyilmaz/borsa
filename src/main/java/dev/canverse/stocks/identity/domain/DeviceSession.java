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
}
