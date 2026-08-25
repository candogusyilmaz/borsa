package dev.canverse.stocks.ledger.infrastructure;

import dev.canverse.stocks.ledger.domain.Reconciliation;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface ReconciliationRepository extends Repository<Reconciliation, UUID> {

    <S extends Reconciliation> S save(S entity);

    @Query(
            "select r from Reconciliation r where r.ownerUserAccountId = :ownerUserAccountId and r.id = :reconciliationId")
    Optional<Reconciliation> findOwned(UUID ownerUserAccountId, UUID reconciliationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select r from Reconciliation r where r.ownerUserAccountId = :ownerUserAccountId and r.id = :reconciliationId")
    Optional<Reconciliation> findOwnedForUpdate(UUID ownerUserAccountId, UUID reconciliationId);

    @Query("select r from Reconciliation r where r.ownerUserAccountId = :ownerUserAccountId"
            + " and r.supersedesReconciliationId = :reconciliationId")
    Optional<Reconciliation> findDirectReplacement(UUID ownerUserAccountId, UUID reconciliationId);
}
