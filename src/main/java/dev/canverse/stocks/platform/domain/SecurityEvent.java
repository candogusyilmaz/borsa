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
@Table(name = "security_event", schema = "platform")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecurityEvent {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_account_id")
    private UserAccount userAccount;

    private String eventType;

    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private String details;
}
