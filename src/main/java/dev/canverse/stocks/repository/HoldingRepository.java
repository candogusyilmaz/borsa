package dev.canverse.stocks.repository;

import dev.canverse.stocks.domain.entity.Holding;
import dev.canverse.stocks.repository.custom.HoldingRepositoryCustom;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HoldingRepository extends BaseJpaRepository<Holding, Long>, HoldingRepositoryCustom {
    @Query("""
                select h from Holding h
                where h.portfolio.id = :portfolioId and h.stock.id = :stockId
                and h.portfolio.user.id = :#{principal.id}
            """)
    Optional<Holding> findByPortfolioIdAndStockIdForPrincipal(Long portfolioId, Long stockId);


    @Modifying
    @Transactional
    @Query("delete from Holding h where h.portfolio.user.id = :#{principal.id}")
    void deleteAllByUserId(Long userId);

    @Query("""
            select h from Holding h
            left join h.trades t on t.id = (select max(tr.id) from Trade tr where tr.holding.id = h.id)
            where h.id = :id and h.portfolio.user.id = :#{principal.id}
            """)
    Optional<Holding> findByIdWithLatestTradeForPrincipal(Long id);

    @Query("select h from Holding h where h.stock.id = :stockId")
    List<Holding> findByStockId(Long stockId);

    @Query("select count(h) > 0 from Holding h where h.portfolio.id = :portfolioId and h.portfolio.user.id = :#{principal.id}")
    boolean isPortfolioOwnedByPrincipal(long portfolioId);
}