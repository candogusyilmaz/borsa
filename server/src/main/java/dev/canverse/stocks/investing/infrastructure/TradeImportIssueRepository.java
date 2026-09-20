package dev.canverse.stocks.investing.infrastructure;

import dev.canverse.stocks.investing.domain.TradeImportIssue;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeImportIssueRepository extends JpaRepository<TradeImportIssue, UUID> {

    List<TradeImportIssue> findAllByOwnerUserAccountIdAndImportBatchId(UUID ownerUserAccountId, UUID importBatchId);
}
