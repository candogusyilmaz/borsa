package dev.canverse.stocks.repository;

import dev.canverse.stocks.domain.entity.instrument.MarketCurrency;
import dev.canverse.stocks.domain.entity.instrument.MarketCurrencyId;
import org.springframework.stereotype.Repository;

@Repository
public interface MarketCurrencyRepository extends BaseJpaRepository<MarketCurrency, MarketCurrencyId> {

}