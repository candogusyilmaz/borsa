package dev.canverse.stocks.repository;

import dev.canverse.stocks.domain.entity.instrument.StockInstrument;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface StockInstrumentRepository extends BaseJpaRepository<StockInstrument, Long> {
    @Query("SELECT s FROM StockInstrument s WHERE s.symbol = :symbol AND s.market.id = :marketId")
    @EntityGraph(attributePaths = {"snapshots"})
    Optional<StockInstrument> findBySymbol(String symbol, Long marketId);
}
