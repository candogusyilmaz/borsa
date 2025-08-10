package dev.canverse.stocks.repository;

import dev.canverse.stocks.domain.entity.instrument.StockInstrument;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockInstrumentRepository extends BaseJpaRepository<StockInstrument, Long> {
    @Query("SELECT s FROM StockInstrument s WHERE s.symbol = :symbol AND s.market.id = :marketId")
    @EntityGraph(attributePaths = {"snapshot"})
    Optional<StockInstrument> findBySymbol(String symbol, Long marketId);
}