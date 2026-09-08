package dev.canverse.stocks.ledger.infrastructure;

import dev.canverse.stocks.ledger.domain.IdempotencyRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    @Query("select r from IdempotencyRecord r where r.ownerUserAccountId = :ownerUserAccountId" +
            " and r.operationScope = :operationScope and r.clientRequestId = :clientRequestId")
    Optional<IdempotencyRecord> findByKey(UUID ownerUserAccountId, String operationScope, UUID clientRequestId);
}
