package dev.canverse.stocks.identity.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
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
}
