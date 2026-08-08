package dev.canverse.stocks.platform.domain;

import dev.canverse.stocks.identity.domain.UserAccount;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "job", schema = "platform")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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
}
