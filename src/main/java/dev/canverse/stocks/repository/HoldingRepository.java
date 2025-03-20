package dev.canverse.stocks.repository;

import dev.canverse.stocks.domain.entity.Holding;
import dev.canverse.stocks.repository.custom.HoldingRepositoryCustom;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HoldingRepository extends BaseJpaRepository<Holding, Long>, HoldingRepositoryCustom {
    Optional<Holding> findByUserIdAndStockId(Long userId, Long stockId);

    void deleteAllByUserId(Long userId);

    @Query("""
            select h from Holding h
            left join h.trades t on t.id = (select max(tr.id) from Trade tr where tr.holding.id = h.id)
            where h.id = :id and h.user.id = :#{principal.id}
            """)
    Optional<Holding> findByIdWithLatestTrade(Long id);

    @Query("select h from Holding h where h.stock.id = :stockId")
    List<Holding> findByStockId(Long stockId);
}