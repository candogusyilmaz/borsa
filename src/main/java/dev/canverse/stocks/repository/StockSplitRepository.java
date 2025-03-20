package dev.canverse.stocks.repository;

import dev.canverse.stocks.domain.entity.StockSplit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockSplitRepository extends BaseJpaRepository<StockSplit, Long> {
    @Query("SELECT s FROM StockSplit s WHERE s.effectiveDate <= :date AND s.processed = false")
    List<StockSplit> findPendingSplits(LocalDate date);

    @Query("""
            SELECT s FROM StockSplit s
            WHERE s.stock.id = :stockId
            AND s.processed = true
            ORDER BY s.effectiveDate DESC
            """)
    Optional<StockSplit> findLatestProcessedSplitByStockId(Long stockId);
}