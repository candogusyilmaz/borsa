package dev.canverse.stocks.ledger.infrastructure;

import dev.canverse.stocks.ledger.domain.MoneyPosting;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MoneyPostingRepository extends JpaRepository<MoneyPosting, UUID> {

    @Query("select p from MoneyPosting p where p.ownerUserAccountId = :ownerUserAccountId and p.activityId = :activityId")
    List<MoneyPosting> findOwnedActivity(UUID ownerUserAccountId, UUID activityId);
}
