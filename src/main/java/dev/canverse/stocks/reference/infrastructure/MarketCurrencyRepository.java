package dev.canverse.stocks.reference.infrastructure;

import dev.canverse.stocks.reference.domain.MarketCurrency;
import dev.canverse.stocks.reference.domain.MarketCurrencyId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketCurrencyRepository extends JpaRepository<MarketCurrency, MarketCurrencyId> {}
