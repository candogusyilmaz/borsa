package dev.canverse.stocks.reference.infrastructure;

import dev.canverse.stocks.reference.domain.Instrument;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface InstrumentRepository extends JpaRepository<Instrument, UUID> {

    @Query("select i from Instrument i where i.id = :id and i.ownerUserAccount.id = :ownerUserAccountId")
    Optional<Instrument> findOwnedById(UUID id, UUID ownerUserAccountId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Instrument i
            set i.version = i.version + 1
            where i.id = :id
              and i.ownerUserAccount.id = :ownerUserAccountId
              and i.version = :expectedVersion
            """)
    int incrementOwnedVersion(UUID id, UUID ownerUserAccountId, long expectedVersion);
}
