package dev.canverse.stocks.repository;

import dev.canverse.stocks.domain.entity.instrument.CustomInstrument;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomInstrumentRepository extends BaseJpaRepository<CustomInstrument, Long> {
    List<CustomInstrument> findAllByUserId(Long userId);

    Optional<CustomInstrument> findByIdAndUserId(Long id, Long userId);

    boolean existsBySymbolAndUserId(String symbol, Long userId);
}
