package dev.canverse.stocks.ledger.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "idempotency_record", schema = "ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRecord {

    @Id
    private UUID id;

    private UUID ownerUserAccountId;
    private String operationScope;
    private UUID clientRequestId;
    private String requestHash;
    private String resultResourceKind;
    private UUID resultResourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    private String resultSnapshot;

    private Instant createdAt;

    public static IdempotencyRecord create(UUID id, UUID ownerUserAccountId, String operationScope, UUID clientRequestId, String requestHash,
            String resultResourceKind, UUID resultResourceId, String resultSnapshot, Instant createdAt) {
        var record = new IdempotencyRecord();
        record.id = Objects.requireNonNull(id, "id");
        record.ownerUserAccountId = Objects.requireNonNull(ownerUserAccountId, "ownerUserAccountId");
        record.operationScope = Objects.requireNonNull(operationScope, "operationScope");
        record.clientRequestId = Objects.requireNonNull(clientRequestId, "clientRequestId");
        record.requestHash = Objects.requireNonNull(requestHash, "requestHash");
        record.resultResourceKind = Objects.requireNonNull(resultResourceKind, "resultResourceKind");
        record.resultResourceId = Objects.requireNonNull(resultResourceId, "resultResourceId");
        record.resultSnapshot = Objects.requireNonNull(resultSnapshot, "resultSnapshot");
        record.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        return record;
    }
}
