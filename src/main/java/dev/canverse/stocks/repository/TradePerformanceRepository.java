package dev.canverse.stocks.repository;

import dev.canverse.stocks.domain.entity.TradePerformance;
import org.springframework.stereotype.Repository;

@Repository
public interface TradePerformanceRepository extends BaseJpaRepository<TradePerformance, Long> {
}