package dev.canverse.stocks.repository;

import dev.canverse.stocks.domain.entity.instrument.Instrument;
import org.springframework.stereotype.Repository;

@Repository
public interface InstrumentRepository extends BaseJpaRepository<Instrument, Long> {
}