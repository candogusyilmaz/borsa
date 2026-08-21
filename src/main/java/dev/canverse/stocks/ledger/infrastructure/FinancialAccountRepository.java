package dev.canverse.stocks.ledger.infrastructure;

import dev.canverse.stocks.ledger.domain.FinancialAccount;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface FinancialAccountRepository extends JpaRepository<FinancialAccount, UUID> {

    @Query("select a from FinancialAccount a where a.id = :accountId and a.ownerUserAccount.id = :ownerUserAccountId")
    Optional<FinancialAccount> findOwned(UUID accountId, UUID ownerUserAccountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from FinancialAccount a where a.id = :accountId and a.ownerUserAccount.id = :ownerUserAccountId")
    Optional<FinancialAccount> findOwnedForUpdate(UUID accountId, UUID ownerUserAccountId);
}
