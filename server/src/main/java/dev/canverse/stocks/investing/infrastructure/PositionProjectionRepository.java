package dev.canverse.stocks.investing.infrastructure;

import dev.canverse.stocks.investing.domain.PositionProjection;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PositionProjectionRepository extends JpaRepository<PositionProjection, UUID> {

    @Query("select p from PositionProjection p where p.ownerUserAccountId = :ownerUserAccountId and" +
            " p.financialAccountId = :financialAccountId and p.instrumentId = :instrumentId")
    Optional<PositionProjection> findOwned(UUID ownerUserAccountId, UUID financialAccountId, UUID instrumentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PositionProjection p where p.ownerUserAccountId = :ownerUserAccountId and" +
            " p.financialAccountId = :financialAccountId and p.instrumentId = :instrumentId")
    Optional<PositionProjection> findOwnedForUpdate(UUID ownerUserAccountId, UUID financialAccountId, UUID instrumentId);
}
