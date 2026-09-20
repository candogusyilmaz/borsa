package dev.canverse.stocks.investing.infrastructure;

import dev.canverse.stocks.investing.domain.TradeImportBatch;
import dev.canverse.stocks.investing.domain.TradeImportFormat;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface TradeImportBatchRepository extends JpaRepository<TradeImportBatch, UUID> {

    @Query("select b from TradeImportBatch b where b.ownerUserAccountId = :ownerUserAccountId and b.id = :batchId")
    Optional<TradeImportBatch> findOwned(UUID ownerUserAccountId, UUID batchId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from TradeImportBatch b where b.ownerUserAccountId = :ownerUserAccountId and b.id = :batchId")
    Optional<TradeImportBatch> findOwnedForUpdate(UUID ownerUserAccountId, UUID batchId);

    Optional<TradeImportBatch> findFirstByOwnerUserAccountIdAndFinancialAccountIdAndImportFormatAndContentSha256(UUID ownerUserAccountId,
            UUID financialAccountId, TradeImportFormat importFormat, String contentSha256);
}
