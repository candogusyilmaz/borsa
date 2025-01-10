package dev.canverse.stocks.repository;

import dev.canverse.stocks.domain.entity.Holding;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HoldingRepository extends BaseJpaRepository<Holding, Long> {
    Optional<Holding> findByUserIdAndStockId(Long userId, Long stockId);
}