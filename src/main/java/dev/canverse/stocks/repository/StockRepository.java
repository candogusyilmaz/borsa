package dev.canverse.stocks.repository;

import dev.canverse.stocks.domain.entity.Stock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockRepository extends BaseJpaRepository<Stock, Long> {
    @Query("SELECT s FROM Stock s WHERE s.symbol = :symbol AND s.exchange.id = :exchangeId")
    @EntityGraph(attributePaths = {"snapshot"})
    Optional<Stock> findBySymbol(String symbol, Long exchangeId);
}