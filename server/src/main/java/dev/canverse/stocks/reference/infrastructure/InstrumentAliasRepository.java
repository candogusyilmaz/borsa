package dev.canverse.stocks.reference.infrastructure;

import dev.canverse.stocks.reference.domain.InstrumentAlias;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface InstrumentAliasRepository extends JpaRepository<InstrumentAlias, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from InstrumentAlias a where a.instrument.id = :instrumentId")
    void deleteByInstrumentId(UUID instrumentId);
}
