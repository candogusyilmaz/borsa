package dev.canverse.stocks.repository;

import dev.canverse.stocks.domain.entity.Holding;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HoldingRepository extends BaseJpaRepository<Holding, Long> {
    Optional<Holding> findByUserIdAndStockId(Long userId, Long stockId);

    void deleteAllByUserId(Long userId);

    @Query("""
            select h from Holding h
            left join h.trades t on t.id = (select max(tr.id) from Trade tr where tr.holding.id = h.id)
            where h.id = :id and h.user.id = :userId
            """)
    Optional<Holding> findByIdWithLatestTrade(Long id, Long userId);
}