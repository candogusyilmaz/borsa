package dev.canverse.stocks.repository;

import dev.canverse.stocks.domain.entity.TradeAnalytic;
import org.springframework.stereotype.Repository;

@Repository
public interface TradeAnalyticsRepository extends BaseJpaRepository<TradeAnalytic, Long> {
}