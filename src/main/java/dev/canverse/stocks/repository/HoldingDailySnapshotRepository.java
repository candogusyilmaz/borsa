package dev.canverse.stocks.repository;

import dev.canverse.stocks.domain.entity.HoldingDailySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface HoldingDailySnapshotRepository extends JpaRepository<HoldingDailySnapshot, Long>, JpaSpecificationExecutor<HoldingDailySnapshot> {
    @Modifying
    @Transactional(timeout = 25)
    @Query(value = """
            INSERT INTO holding_daily_snapshots (holding_id,
                                                   quantity,
                                                   average_price,
                                                   market_price,
                                                   previous_market_price,
                                                   portfolio_weight_percentage,
                                                   created_at)
            WITH portfolio_totals AS (
                SELECT h.user_id,
                       SUM(h.quantity * ss.last) AS total_portfolio_value
                FROM holdings h
                JOIN stock_snapshots ss ON h.stock_id = ss.stock_id
                WHERE h.quantity > 0
                GROUP BY h.user_id)
            SELECT h.id,
                   h.quantity,
                   round(h.total / h.quantity, 2),
                   ss.last,
                   ss.close,
                   CASE
                       WHEN pt.total_portfolio_value > 0 THEN (h.quantity * ss.last / pt.total_portfolio_value) * 100
                       ELSE 0
                   END,
                   now()
            FROM holdings h
            JOIN stock_snapshots ss ON h.stock_id = ss.stock_id
            JOIN portfolio_totals pt ON h.user_id = pt.user_id
            WHERE h.quantity > 0
            """, nativeQuery = true)
    void generateDailyHoldingSnapshots();
}