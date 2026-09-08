package dev.canverse.stocks.ledger.infrastructure;

import dev.canverse.stocks.ledger.domain.AccountCashPocket;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AccountCashPocketRepository extends JpaRepository<AccountCashPocket, UUID> {

    @Query("select p from AccountCashPocket p where p.financialAccount.id = :accountId")
    Optional<AccountCashPocket> findByAccountId(UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from AccountCashPocket p where p.financialAccount.id = :accountId")
    Optional<AccountCashPocket> findByAccountIdForUpdate(UUID accountId);
}
