package dev.canverse.stocks.repository;

import dev.canverse.stocks.domain.entity.StockExchangeMapping;
import org.springframework.stereotype.Repository;

@Repository
public interface StockExchangeMappingRepository extends BaseJpaRepository<StockExchangeMapping, Long> {
}