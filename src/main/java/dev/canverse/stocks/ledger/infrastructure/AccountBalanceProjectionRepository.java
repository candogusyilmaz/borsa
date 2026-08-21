package dev.canverse.stocks.ledger.infrastructure;

import dev.canverse.stocks.ledger.domain.AccountBalanceProjection;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AccountBalanceProjectionRepository extends JpaRepository<AccountBalanceProjection, UUID> {

    @Query("select p from AccountBalanceProjection p where p.ownerUserAccountId = :ownerUserAccountId"
            + " and p.financialAccount.id = :accountId")
    Optional<AccountBalanceProjection> findOwned(UUID ownerUserAccountId, UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from AccountBalanceProjection p where p.ownerUserAccountId = :ownerUserAccountId"
            + " and p.financialAccount.id = :accountId")
    Optional<AccountBalanceProjection> findOwnedForUpdate(UUID ownerUserAccountId, UUID accountId);
}
