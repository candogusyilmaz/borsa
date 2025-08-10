package dev.canverse.stocks.repository;

import dev.canverse.stocks.domain.entity.instrument.Market;
import org.springframework.stereotype.Repository;

@Repository
public interface MarketRepository extends BaseJpaRepository<Market, Long> {
    Market findByCode(String code);
}