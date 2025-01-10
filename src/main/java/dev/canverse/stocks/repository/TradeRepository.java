package dev.canverse.stocks.repository;

import dev.canverse.stocks.domain.entity.Trade;
import org.springframework.stereotype.Repository;

@Repository
public interface TradeRepository extends BaseJpaRepository<Trade, Long> {
}