package dev.canverse.stocks.investing.infrastructure;

import dev.canverse.stocks.investing.domain.TradeImportRow;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeImportRowRepository extends JpaRepository<TradeImportRow, UUID> {

    List<TradeImportRow> findAllByOwnerUserAccountIdAndImportBatchIdOrderBySourceRecordNumberAsc(UUID ownerUserAccountId, UUID importBatchId);
}
