package dev.canverse.stocks.platform;

import dev.canverse.stocks.identity.UserAccount;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "job", schema = "platform")
public class Job {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_account_id")
    private UserAccount ownerUserAccount;

    private String jobType;

    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    private Instant availableAt;

    private String claimedBy;

    private UUID claimToken;

    private Instant claimedAt;

    private Instant heartbeatAt;

    private int attemptCount;

    private int maxAttempts;

    private Instant completedAt;

    private String lastError;

    private Instant createdAt;

    private Instant updatedAt;

    protected Job() {}

    public UUID getId() {
        return id;
    }

    public UserAccount getOwnerUserAccount() {
        return ownerUserAccount;
    }

    public String getJobType() {
        return jobType;
    }

    public String getStatus() {
        return status;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getAvailableAt() {
        return availableAt;
    }

    public String getClaimedBy() {
        return claimedBy;
    }

    public UUID getClaimToken() {
        return claimToken;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public Instant getHeartbeatAt() {
        return heartbeatAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
