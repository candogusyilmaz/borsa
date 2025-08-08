package dev.canverse.stocks.repository;

import dev.canverse.stocks.domain.entity.portfolio.Position;
import dev.canverse.stocks.repository.custom.PositionRepositoryCustom;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PositionRepository extends BaseJpaRepository<Position, Long>, PositionRepositoryCustom {
    @Query("""
                select h from Position h
                where h.portfolio.id = :portfolioId and h.stock.id = :stockId
                and h.portfolio.user.id = :#{principal.id}
            """)
    Optional<Position> findByPortfolioIdAndStockIdForPrincipal(Long portfolioId, Long stockId);


    @Modifying
    @Transactional
    @Query("delete from Position h where h.portfolio.user.id = :#{principal.id}")
    void deleteAllByUserId(Long userId);

    @Query("""
            select h from Position h
            left join h.transactions t on t.id = (select max(tr.id) from Transaction tr where tr.position.id = h.id)
            where h.id = :id and h.portfolio.user.id = :#{principal.id}
            """)
    Optional<Position> findByIdWithLatestTradeForPrincipal(Long id);

    @Query("select h from Position h where h.stock.id = :stockId")
    List<Position> findByStockId(Long stockId);

    @Query("select count(h) > 0 from Position h where h.portfolio.id = :portfolioId and h.portfolio.user.id = :#{principal.id}")
    boolean isPortfolioOwnedByPrincipal(long portfolioId);
}