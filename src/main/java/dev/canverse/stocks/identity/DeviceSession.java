package dev.canverse.stocks.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device_session", schema = "identity")
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

    protected DeviceSession() {}

    public UUID getId() {
        return id;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public String getRefreshTokenHash() {
        return refreshTokenHash;
    }

    public String getDeviceLabel() {
        return deviceLabel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getRevokeReason() {
        return revokeReason;
    }

    public UUID getReplacedBySessionId() {
        return replacedBySessionId;
    }
}
