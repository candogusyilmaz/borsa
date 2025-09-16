package dev.canverse.stocks.repository;

import dev.canverse.stocks.domain.entity.portfolio.Position;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PositionRepository extends BaseJpaRepository<Position, Long> {
    @Query("""
                select h from Position h
                where h.portfolio.id = :portfolioId and h.instrument.id = :instrumentId
                and h.portfolio.user.id = :#{principal.id}
            """)
    Optional<Position> findByPortfolioAndInstrumentForPrincipal(Long portfolioId, Long instrumentId);


    @Modifying
    @Transactional
    @Query("delete from Position h where h.portfolio.user.id = :#{principal.id}")
    void deleteAllByUserId(Long userId);

    @Query("select h from Position h where h.id = :id and h.portfolio.user.id = :userId")
    Optional<Position> findByIdAndUserId(Long id, Long userId);

}